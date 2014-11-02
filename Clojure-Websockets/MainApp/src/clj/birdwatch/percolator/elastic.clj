(ns birdwatch.percolator.elastic
  (:gen-class)
  (:require
   [clojure.tools.logging :as log]
   [pandect.core :refer [sha1]]
   [clojure.pprint :as pp]
   [clojurewerkz.elastisch.rest.percolation :as perc]
   [clojurewerkz.elastisch.rest.response    :as esrsp]
   [clojure.core.async :as async :refer [<! put! go-loop]]))

(defn start-percolator
  "register percolation search with ID based on hash of the query"
  [{:keys [query uid]} conn subscriptions]
  (let [sha (sha1 (str query))]
    (swap! subscriptions assoc uid sha)
    (perc/register-query conn "percolator" sha :query query)
    (log/debug "Percolation registered for query" query "with SHA1" sha)))

(defn run-percolation-register-loop
  "loop for finding percolation matches and delivering those on the appropriate socket"
  [register-percolation-chan conn subscriptions]
  (go-loop [] (let [params (<! register-percolation-chan)]
                (start-percolator params conn subscriptions)
                (recur))))

(defn run-percolation-loop
  "loop for finding percolation matches and delivering those on the appropriate socket"
  [percolation-chan percolation-matches-chan conn subscriptions]
  (go-loop [] (let [t (<! percolation-chan)
                    response (perc/percolate conn "percolator" "tweet" :doc t)
                    matches (set (map :_id (esrsp/matches-from response)))] ;; set with SHAs
                (put! percolation-matches-chan [t matches @subscriptions]) ;; send deref'd subscriptions as val
                (recur))))