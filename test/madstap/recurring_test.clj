(ns madstap.recurring-test
  (:require
   [clojure.test :refer [deftest testing is are run-tests test-var]]
   [tick.core :as t]
   [madstap.recurring :as rec]))

(deftest simple-times-of-day-test
  (is (= [#time/offset-date-time "2019-09-04T01:30+01:00"
          #time/offset-date-time "2019-09-05T01:30+01:00"
          #time/offset-date-time "2019-09-06T01:30+01:00"]
         (take 3 (rec/times #time/instant "2019-09-03T22:00:00Z"
                            [#time/time "01:30" #time/zone "+01:00"]))))
  (is (= [#time/zoned-date-time "2019-09-04T01:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-05T01:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-06T01:30-03:00[America/Sao_Paulo]"]
         (take 3 (rec/times #time/instant "2019-09-03T22:00:00Z"
                            [#time/time "01:30" #time/zone "America/Sao_Paulo"])))))
