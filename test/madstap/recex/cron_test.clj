(ns madstap.recex.cron-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is are run-tests test-var]]
   [madstap.recex :as recex]))

(defn recex= [& recexes]
  (when-not (every? recex/valid? recexes)
    (throw (ex-info (-> (keep recex/explain-str recexes) (str/join "\n"))
                    {:invalid-recexes (remove recex/valid? recexes)})))
  (apply = (map recex/normalize recexes)))

(def every-min {:m {0 59} :h {0 23}})

(deftest cron->recex
  (is (recex= #{[2 {:m 0 :h 2} "Europe/Oslo"]
                [:tue {:m 0 :h 2} "Europe/Oslo"]} (recex/cron->recex "0 2 2 * 2" "Europe/Oslo")))
  (is (recex= [{:m 0 :h 2} "Europe/Oslo"] (recex/cron->recex "0 2 * * *" "Europe/Oslo")))
  (is (recex= [{:s 30 :m {0 59}, :h {0 23}}] (recex/cron->recex "30 * * * * *")))
  (is (recex= [{:m {0 59}, :h #{0 20 10}}] (recex/cron->recex "* /10 * * *")))
  (is (recex= [{:m {2 4}, :h #{4 6 2}}] (recex/cron->recex "2-4  2-6/2 * * *")))
  (is (recex= [{:m {2 4}, :h #{20 22}}] (recex/cron->recex "2-4  20/2 * * *")))
  (is (recex= [{:m {2 4}, :h #{1 2 20 22}}] (recex/cron->recex "2-4  1,2,20/2 * * *")))
  (is (recex= [{:m #{56 58}, :h {0 23}}] (recex/cron->recex "56/2  * * * *")))
  (is (recex= [:sun every-min] (recex/cron->recex "* * * * 7")))
  (is (recex= [{:tue :fri} every-min] (recex/cron->recex "* * * * 2-5")))
  (is (recex= [{:sun :fri} every-min] (recex/cron->recex "* * * * 0-5")))
  (is (recex= [{:fri :sun} every-min] (recex/cron->recex "* * * * 5-7")))
  (is (recex= [:feb every-min] (recex/cron->recex "* * * 2 *")))
  (is (recex= [:feb every-min] (recex/cron->recex "* * * 2 *")))
  (is (recex= [#{:aug :oct :dec} every-min] (recex/cron->recex "* * * 8/2 *")))
  (is (recex= [#{:feb #{:aug :oct :dec}} every-min] (recex/cron->recex "* * * 2,8/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} every-min] (recex/cron->recex "* * * 2,8-10/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} every-min] (recex/cron->recex "* * * feb,aug-oct/2 *")))
  (is (recex= [#{:feb #{:aug :oct}} :mon every-min] (recex/cron->recex "* * * feb,aug-oct/2 mon")))
  (is (recex= [#{#{:aug :oct :dec}} every-min] (recex/cron->recex "* * * aug/2 *")))
  (is (recex= [#{29 30 31} every-min] (recex/cron->recex "* * 29/1 * *")))
  (is (recex= [#{#{:aug :sep :oct :nov :dec}} every-min] (recex/cron->recex "* * * aug/1 *")))
  (is (recex= #{[:feb {:tue :thur} every-min]
                [:feb 2 every-min]           } (recex/cron->recex "* * 2 2 2-4")))
  (is (recex= #{[{:tue :thur} every-min]
                [{2 10} every-min]           } (recex/cron->recex "* * 2-10 * 2-4")))
  (is (recex= [[1 :mon] {:m 0 :h 0}] (recex/cron->recex "0 0 * * mon#1")))
  (is (recex= [[-1 :fri] {:m 0 :h 0}] (recex/cron->recex "0 0 * * friL")))
  (is (recex= [[-3 :fri] {:m 0 :h 0}] (recex/cron->recex "0 0 * * friL-3")))
  (is (recex= [-1 {:m 0 :h 0}] (recex/cron->recex "0 0 L * *")))
  (is (recex= [-20 {:m 0 :h 0}] (recex/cron->recex "0 0 L-20 * *"))))
