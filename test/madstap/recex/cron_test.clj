(ns madstap.recex.cron-test
  (:require
   [madstap.recex :as recex]
   [madstap.recex.cron :as cron]
   [clojure.test :refer [deftest testing is are run-tests test-var]]))

(defn recex= [& recexes]
  (assert (every? recex/valid? recexes))
  (apply = (map recex/normalize recexes)))

(def every-min {:m {0 59} :h {0 23}})

(deftest cron->recex
  (is (recex= [{:m {2 4}, :h #{4 6 2}}] (cron/cron->recex "2-4  2-6/2 * * *")))
  (is (recex= [:sun every-min] (cron/cron->recex "* * * * 7")))
  (is (recex= [{:tue :fri} every-min] (cron/cron->recex "* * * * 2-5")))
  (is (recex= [:feb every-min] (cron/cron->recex "* * * 2 *")))
  (is (recex= [:feb every-min] (cron/cron->recex "* * * 2 *")))
  (is (recex= [#{:aug :oct :dec} every-min] (cron/cron->recex "* * * 8/2 *")))
  (is (recex= [#{:feb #{:aug :oct :dec}} every-min] (cron/cron->recex "* * * 2,8/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} every-min] (cron/cron->recex "* * * 2,8-10/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} every-min] (cron/cron->recex "* * * feb,aug-oct/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} :mon every-min] (cron/cron->recex "* * * feb,aug-oct/2 mon")))
  (is (recex= #{[:feb {:tue :thur} every-min]
                [:feb 2 every-min]           } (cron/cron->recex "* * 2 2 2-4")))
  (is (recex= #{[{:tue :thur} every-min]
                [{2 10} every-min]           } (cron/cron->recex "* * 2-10 * 2-4"))))
