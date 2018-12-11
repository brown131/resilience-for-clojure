(defproject resilience-for-clojure "0.1.0-SNAPSHOT"
  :description "A clojure wrapper for Resilience4j" 
  :url "https://github.com/ylgrgyq/resilience-for-clojure"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [io.github.resilience4j/resilience4j-all "0.13.2"]]
  :plugins [[lein-codox "0.9.5"]]
  :codox {:output-path "target/codox"
          :source-uri "https://github.com/ylgrgyq/resilience-for-clojure/blob/master/{filepath}#L{line}"
          :metadata {:doc/format :markdown}}
  :deploy-repositories {"releases" :clojars}
  :global-vars {*warn-on-reflection* true
                *assert* false})
