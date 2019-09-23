(ns madstap.recex.cron-test
  (:require
   [madstap.recex :as recex]
   [madstap.recex.cron :as cron]
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is are run-tests test-var]]))

(defn recex= [& recexes]
  (if-not (every? recex/valid? recexes)
    (throw (ex-info (-> (keep recex/explain-str recexes) (str/join "\n"))
                    {:invalid-recexes (remove recex/valid? recexes)})))
  (apply = (map recex/normalize recexes)))

(def every-min {:m {0 59} :h {0 23}})

(deftest cron->recex
  (is (recex= [{:m {0 59}, :h #{0 20 10}}] (cron/cron->recex "* /10 * * *")))
  (is (recex= [{:m {2 4}, :h #{4 6 2}}] (cron/cron->recex "2-4  2-6/2 * * *")))
  (is (recex= [{:m {2 4}, :h #{20 22}}] (cron/cron->recex "2-4  20/2 * * *")))
  (is (recex= [{:m {2 4}, :h #{1 2 20 22}}] (cron/cron->recex "2-4  1,2,20/2 * * *")))
  (is (recex= [{:m #{56 58}, :h {0 23}}] (cron/cron->recex "56/2  * * * *")))
  (is (recex= [:sun every-min] (cron/cron->recex "* * * * 7")))
  (is (recex= [{:tue :fri} every-min] (cron/cron->recex "* * * * 2-5")))
  (is (recex= [{:sun :fri} every-min] (cron/cron->recex "* * * * 0-5")))
  (is (recex= [{:fri :sun} every-min] (cron/cron->recex "* * * * 5-7")))
  (is (recex= [:feb every-min] (cron/cron->recex "* * * 2 *")))
  (is (recex= [:feb every-min] (cron/cron->recex "* * * 2 *")))
  (is (recex= [#{:aug :oct :dec} every-min] (cron/cron->recex "* * * 8/2 *")))
  (is (recex= [#{:feb #{:aug :oct :dec}} every-min] (cron/cron->recex "* * * 2,8/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} every-min] (cron/cron->recex "* * * 2,8-10/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} every-min] (cron/cron->recex "* * * feb,aug-oct/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} :mon every-min] (cron/cron->recex "* * * feb,aug-oct/2 mon")))
  (is (recex= [#{#{:aug :oct :dec}} every-min] (cron/cron->recex "* * * aug/2 *")))
  (is (recex= [#{29 30 31} every-min] (cron/cron->recex "* * 29/1 * *")))
  (is (recex= [#{#{:aug :sep :oct :nov :dec}} every-min] (cron/cron->recex "* * * aug/1 *")))
  (is (recex= #{[:feb {:tue :thur} every-min]
                [:feb 2 every-min]           } (cron/cron->recex "* * 2 2 2-4")))
  (is (recex= #{[{:tue :thur} every-min]
                [{2 10} every-min]           } (cron/cron->recex "* * 2-10 * 2-4"))))
