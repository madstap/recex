(ns madstap.recex.cron-test
  (:require
   [madstap.recex :as recex]
   [madstap.recex.cron :as cron]
   [clojure.test :refer [deftest testing is are run-tests test-var]]))

(defn recex= [& recexes]
  (apply = (map recex/normalize recexes)))

(def every-min {:m {0 59} :h {0 23}})

(deftest cron->recex
  (is (recex= [{:m {2 4}, :h #{4 6 2}}] (cron/cron->recex "2-4  2-6/2 * * *")))
  (is (recex= [:sun every-min] (cron/cron->recex "* * * * 7")))
  #_(is (recex= [#{:aug :oct :dec} every-min] (cron/cron->recex "* * * 8/2 *"))))
