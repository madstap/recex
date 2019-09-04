(ns madstap.recex-test
  (:require
   [clojure.test :refer [deftest testing is are run-tests test-var]]
   [tick.core :as t]
   [madstap.recex :as rec]))

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
                            [#time/time "01:30" #time/zone "America/Sao_Paulo"]))))

  (is (= #time/zoned-date-time "2019-09-05T01:00+02:00[Europe/Oslo]"
         (first (rec/times
                 ;; The same instant as:
                 ;; #time/zoned-date-time "2019-09-04T03:00+02:00[Europe/Oslo]"
                 #time/instant "2019-09-04T01:00:00Z"
                 [#time/time "01:00" #time/zone "Europe/Oslo"]))))

  (is (= [#time/zoned-date-time "2019-09-04T01:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-04T02:30-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-05T01:30-03:00[America/Sao_Paulo]"]
         (take 3 (rec/times #time/instant "2019-09-03T22:00:00Z"
                            [#{#time/time "01:30"
                               #time/time "02:30"}
                             #time/zone "America/Sao_Paulo"])))))

(deftest combining-recexes
  (is (= [#time/zoned-date-time "2019-09-04T12:00+02:00[Europe/Oslo]"
          #time/zoned-date-time "2019-09-04T12:00-03:00[America/Sao_Paulo]"
          #time/zoned-date-time "2019-09-05T12:00+02:00[Europe/Oslo]"]
         (take 3 (rec/times #time/instant "2019-09-03T22:00:00Z"
                            #{[#time/time "12:00" #time/zone "Europe/Oslo"]
                              [#time/time "12:00" #time/zone "America/Sao_Paulo"]})))))
