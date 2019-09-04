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

(deftest friday-13th
  (is (= [#time/zoned-date-time "2019-09-13T00:00Z[UTC]"
          #time/zoned-date-time "2019-12-13T00:00Z[UTC]"
          #time/zoned-date-time "2020-03-13T00:00Z[UTC]"
          #time/zoned-date-time "2020-11-13T00:00Z[UTC]"]
         (take 4
               (rec/times #time/instant "2019-01-01T00:00:00Z"
                          [#time/day-of-week "FRIDAY"
                           13
                           #time/time "00:00"])))))

(deftest triple-witching-days
  (is (= [#time/zoned-date-time "2019-03-15T15:00-04:00[America/New_York]"
          #time/zoned-date-time "2019-06-21T15:00-04:00[America/New_York]"
          #time/zoned-date-time "2019-09-20T15:00-04:00[America/New_York]"
          #time/zoned-date-time "2019-12-20T15:00-05:00[America/New_York]"]
         (take 4
               (rec/times
                #time/zoned-date-time "2019-01-01T00:00-05:00[America/New_York]"
                [#{#time/month "MARCH"     #time/month "JUNE"
                   #time/month "SEPTEMBER" #time/month "DECEMBER"}
                 [3 #time/day-of-week "FRIDAY"]
                 #time/time "15:00"
                 #time/zone "America/New_York"])))))

(deftest filters
  (is (true?
       ((rec/month-filter #time/month "SEPTEMBER")
        #time/zoned-date-time "2019-09-04T14:48:55.382-03:00[America/Sao_Paulo]")))
  (is (false?
       ((rec/month-filter #time/month "JANUARY")
        #time/zoned-date-time "2019-09-04T14:48:55.382-03:00[America/Sao_Paulo]")))
  (is (true?
       ((rec/day-of-week-filter #time/day-of-week "MONDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (false?
       ((rec/day-of-week-filter #time/day-of-week "FRIDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (true?
       ((rec/nth-day-of-week-filter 5 #time/day-of-week "MONDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (false?
       ((rec/nth-day-of-week-filter 4 #time/day-of-week "MONDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]")))
  (is (false?
       ((rec/nth-day-of-week-filter 5 #time/day-of-week "FRIDAY")
        #time/zoned-date-time "2019-09-30T14:39:39.983-03:00[America/Sao_Paulo]"))))
