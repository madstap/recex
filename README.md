# recex

A recurrence expression, or a recex, is a domain specific language to
express an infinite series of recurring times.

## Project maturity

Alpha. Breaking changes unlikely, but possible.

## Quickstart

Add dependency

```clojure
;; deps.edn
madstap/recex {:mvn/version "0.1.0}

;; lein/boot
[madstap/recex "0.1.0"]
```

Generate schedule

```clojure
(ns my.app
  (:require
   [madstap.recex :as recex]
   [tick.alpha.api :as t]))

(take 2 (recex/times ["12:00" "Europe/London"] (t/now)))
;; => (#time/zoned-date-time "2019-09-26T12:00+01:00[Europe/London]"
;;     #time/zoned-date-time "2019-09-27T12:00+01:00[Europe/London]")
```

[Docs](#Usage)

## Rationale

### Domain specific languages

Dsls make a tradeoff between flexibility and leverage. With a general purpose
language you can say anything, they're all turing complete, thus
infinitely flexible. With a domain specific one however, you can say
thing much more concisely, expressing yourself on the level of the
domain, but you can only say things using the primitives the language
creator thought of.

Recex aims to be good at tersely expressing a series of recurring
times, for things like scheduling, without having to write code. It
can also serve as the starting point for generating a sequence of
times that is then manipulated by code that does something recexes
can't express.

### Infinite sequences

[Chime](https://github.com/jarohen/chime#the-big-idea-behind-chime) is
a nice, simple scheduler for clojure. It takes a sequence of times and
executes a funtion at those times. The docs shows you how to write the
code to generate the sequences for some standard use-cases, but when
using the library that way I ran into some issues.

The first one is that infinite sequences are unwieldy. Even though
modern clojure environments limit the number of elemnts printed to
less than infinity, printing a context map with a bunch of them still
makes my emacs stutter. It also makes the printout pretty unreadable.

The second one is that you might want to make the schedule configurable.
If I want a job to execute at 10:30 every day instead of at 9, that should
ideally involve changing a config file, and not changing code.

They do mention that you can use a dsl to generate the sequence that you
feed into chime, which is exactly what I [recommend doing](#Chime) with recex.

### Structured data

Since this is clojure, we prefer our dsls to be defined in terms of data,
instead of text. This makes it easy to write programs that generate the dsl,
and it also makes it much easier to parse. Existing dsls in this domain,
like cron and [schyntax](https://github.com/schyntax/schyntax), are
not defined in terms of structured data.

## Usage

### recex grammar

A recex is a vector with slots:

``` Clojure
[month day-of-week day-of-month time time-zone dst-options]
```

The different slots are `AND`ed together, while sets in a single slot means `OR`.

(Except for the daylight saving time (dst) options, which is explained further down.)

Some examples are worth a thousand words:

```clojure
;; Each day at 6 in the afternoon.
["18:00"]

;; When not specified, the time defaults to midnight and time zone defaults to UTC.
;; These are all the same:
[]
["00:00"]
["00:00" "UTC"]
["UTC"]

;; 9 and 17 every day in Oslo time
[#{"09:00" "17:00"} "Europe/Oslo"]

;; Noon in both Oslo and São Paulo time
["12:00" #{"Europe/Oslo" "America/Sao_Paulo"}]

;; Mondays and fridays at 2 in the afternoon in LA
[#{:monday :friday} "14:00" "America/Los_Angeles"]

;; Every february 29th (every 4 years)
[:february 29]

;; Midnight every friday the 13th
[:friday 13 "00:00"]

;; Every friday the 13th in august.
[:august :friday 13]
```

Days of the month can be negative, counting backwards from the end.

```clojure
;; The last day in february
[:february -1]
```

A day of the week can be specified to be the nth day of the week in that month.

```clojure
;; https://en.wikipedia.org/wiki/Triple_witching_hour
;; 15:00 New York time every third friday in March, June, September and December.
[#{:march :june :september :december} [3 :friday] "15:00" "America/New_York"]

;; Like day of month, they can also be negative.

;; The last monday in june.
[:june [-1 :monday]]
```

Months, days of the week and days in months can be specified using ranges.
A range is a map of `{start end}` (inclusive).

```clojure
;; Noon on weekdays in Finland.
[{:monday :friday} "12:00" "Europe/Helsinki"]

;; Noon every day from the 10th to the 15th from October through March and also June
[#{{:october :march} :june} {10 15} "12:00"]
```

Multiple recexes can be combined by using a set.

```clojure
;; Noon in Oslo time and 10 in São Paulo
#{["12:00" "Europe/Oslo"]
  ["10:00" "America/Sao_Paulo"]}
```

### Time expressions

For more granular control, and less repetition, times can be
substituted with time expressions. They are maps with one or more of
`:h`, `:m` and `:s` keys, which mean hour, minute and second.

Each can be either an integer, a set of integers or a range.

When a smaller unit is set, the larger ones not set are considered the set
of all possible values and when a larger one is set,
the smaller ones not set are considered 0. If a unit in the middle is not set,
then it is also considered the set of all possible values.

This is awkward to explain with words, but (hopefully) intuitive.
Examples might help:

```clojure
;; Equivalent ways to say 10:30 and 12:30
[{:h #{10 12} :m 30}]
[{:h #{10 12} :m 30 :s 0}]
[#{"10:30" "12:30"}]

;; Equivalent ways to say every half hour
[{:m #{0 30}]
[{:h {0 23} :m #{0 30}}]

;; Every half a minute from 10 to 12
[{:h {10 12} :s #{0 30}}]
```

### Daylight saving time

Daylight saving time is a terrible thing, I'm sure we can all agree,
but we need to handle those edge cases.

Unless you live in Iceland. If you write software only for use in Iceland
you can ignore this section, you lucky bastard.

Recex doesn't try to be smart and guess at how you want to handle them,
because there are valid reasons to choose different approaches,
depending on what you're doing.

There are two different edge cases when talking about dst. An overlap
is when the clocks are turned back, which means there is a period of
time, usually an hour, that repeats, but in different offsets. A gap
is when the clocks are turned forwards and there's (usually) an hour
that doesn't occur.

#### Overlap

There are three options for handling an overlap: `:first`, `:second` or `:both`.
The default is `:first`.

On the 1st of november in 2015, America/Los_Angeles turned the clocks
back so the hour between 01:00 and 02:00 repeated, the first one in
offset -07:00 and the second in -08:00.

If you want a batch job to run every night at 01:30, you're probably
best off using the default, `:first`. This means that it happens only
once, in offset -07:00.

```clojure
;; These are the same:
["01:30" "America/Los_Angeles"]
["01:30" "America/Los_Angeles" {:dst/overlap :first}]
```

If you need an alarm clock to wake up for your night shift, however,
you probably want `:second`, to take advantage of that extra hour of sleep.
It'll ring once, in offset -08:00.

```clojure
["01:30" "America/Los_Angeles" {:dst/overlap :second}]
```

And if you need something to run every half hour from midnight to
noon, and don't want there to be any gaps, you want `:both`. It'll run
two times more that day than usual, but it'll follow the requirements exactly.

```clojure
;; Every half hour from midnight to noon (inclusive)
[#{{:h {0 11} :m #{0 30}} "12:00"} "America/Los_Angeles" {:dst/overlap :both}]
```

#### Gap

Gaps have two options `:skip` and `:include`. The default is `:include`.

On the 31st of march 2019, Norway turned the clocks forward. The hour
from 02:00 to 03:00 was skipped.

If you have a batch job running at 02:30 each night, you want the default: `:include`.
It'll run at 03:30 instead that night.

```clojure
;; These are the same:
["02:30" "Europe/Oslo"]
["02:30" "Europe/Oslo" {:dst/gap :include}]
```

Continuing with the example from above, if you're running something
every half hour from midnight to noon, you'll want `:skip`. If you
didn't specify `:skip` you'd get every time between 02:00 and 03:00
twice.

```clojure
[#{{:h {0 11} :m #{0 30}} "12:00"} "Europe/Oslo" {:dst/gap :skip}]
```

To avoid both repeating times (when there's a gap) and
skipping them (when there's an overlap)
for schedules of this kind, you'll want to use the following dst opts.

```clojure
[#{{:h {0 11} :m #{0 30}} "12:00"} "Europe/Oslo" {:dst/overlap :both
                                                  :dst/gap :skip}]
```

### Java time objects

While the examples up to this point has used keywords for days of week
and monhts, and strings for times and time-zones, this is a
shorthand syntax. They are translated to java time objects, and you
can use java time objects directly, which might be handy when
generating recexes. Recex depends on [tick](git@github.com:juxt/tick.git)
which defines reader literals for these.
(Which can be [configured](https://juxt.pro/tick/docs/index.html#_serialization).)

```clojure
;; These are the same:
[#time/month "AUGUST" #time/day-of-week "FRIDAY" #time/time "12:00" #time/zone "Europe/Oslo"]
[:august :friday "12:00" "Europe/Oslo"]
[:aug :fri "12:00" "Europe/Oslo"]
```

### Clojure API

Require the namespace:

```clojure
(ns my.app
  (:require
   [madstap.recex :as recex]
   [tick.alpha.api :as t]))
```

The `recex/times` function generates an infinite sequence of
`java.time.ZonedDatetime`s from a recex.

It takes a recex and a time `now` which can be either an instant, zoned-date-time
or offset-date-time, (ie anything that represents a point on the global timeline).

`now` can be omitted, in which case it will use `t/now`, but it's
not recommended to use the implicit now except for experimenting at the repl.
Omitting the `now` argument makes the function impure, and thus trickier to test.

(Remember to `take` when experimenting at the repl, it is an infinite sequence.)

```Clojure
(take 2 (recex/times ["00:00"] (t/zoned-date-time "2019-09-06T00:00Z[UTC]")))

(take 2 (recex/times ["00:00"])) ; Implicit `(t/now)`.
```

An exception will be thrown on an impossible combination of month and day
or month, nth day of week and day.

```clojure
;; These all throw exceptions:

(recex/times [[-1 :friday] 1])
(recex/times [[1 :monday] -1]
(recex/times [:february 31])

;; While probably a mistake, this won't throw as it's still possible
;; to generate times, it'll ignore the 31
(recex/times [:february #{29 31}])
```

The `recex/valid?` predicate can be used to check a recex before
it is passed to `recex/times`.


#### Cron strings

There's also an util function for compiling cron strings to recexes.

```clojure
(require '[madstap.recex.cron :as cron])

(cron/cron->recex "/20 12 * * 2-5")
;=> [{#time/day-of-week "TUESDAY" #time/day-of-week "FRIDAY"} {:m #{0 20 40}, :h 12}]
```

It accepts both the standard 5 slot version of cron, and 6 slot ones with seconds.

```clojure
(cron/cron->recex "30 0 0 * * *")
;=> [{:s 30, :m 0, :h 0}]
```

Cron strings are compiled to recexes without a specific time zone
(defaulting to UTC), but `cron->recex` has a second arity where a
time zone can be specified.

```clojure
(cron/cron->recex "0 12 * * *" "America/New_York")
;;=> [{:m 0, :h 12} "America/New_York"]
```

The cron strings also support most of the features from the
[Quartz dialect](http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html)
of cron, with the exception of the `W` special character
(see [#3](https://github.com/madstap/recex/issues/3)) and the year slot.

The weekdays of quartz cron (1-7 = sun-sat) are different from standard cron (0-6 = sun-sat),
and recex follows the original cron plus the non-standard (7 = sun).

```clojure
;; Noon last day of february
(cron/cron->recex "0 12 L 2 *")

;; Triple witching hour
(cron/cron->recex "0 15 * 3,6,9,12 fri#3" "America/New_York")
```

### Chime

To use recex together with chime it's necessary to translate
`java.time.ZonedDateTime`s to joda time, because chime was written
before java.time was a thing.

(Needs [clj-time](https://github.com/clj-time/clj-time) as a dependency.)

```clojure
(require '[clj-time.coerce :as ct.coerce])

(defn zdt->joda-time [zdt]
  (-> zdt .toInstant java.util.Date/from ct.coerce/from-date))
```

(Maybe someone could port chime to java.time)

## Prior art

### cron

cron is the de-facto standard time dsl. It is text-based, and not very
readable (YMMV). Time zones are not part of the expression itself, it
uses the system time zone or you can choose the time zone with config files
or env vars, which is quite fiddly.

### [Quartz](http://www.quartz-scheduler.org/)

A java library which notably has a dialect of cron with additional features,
and a java api that does a whole lot of things, many outside the scope of recex.
Features it has that recex doesn't and I'd be interested to add are
"nearest weekday to nth of month" and some way to take into consideration holidays.

### [timely](https://github.com/Factual/timely)

A clojure DSL for scheduling. It's a nicer syntax (defined in terms of
clojure function calls) that compiles to cron strings, coupled with a simple
scheduler. Being a 1-1 translation to cron, it shares the pros and cons
of cron's semantics, except aditionally there's a way to delimit schedules to
being valid only between two points on the timeline.

### [schyntax](https://github.com/schyntax/schyntax)

A time dsl with implementations in js, .NET and go. It has pretty
similar features to recex, but also easily expresses "every n minutes"
type repetitions and has negations, which I'd be interested to add to
recexes.  It works only in UTC, however, which makes it hard or impossible to
express things involving time zones with dst.

### [iCalendar recurrence rules](https://www.kanzaki.com/docs/ical/rrule.html)

[RFC 2445](https://www.ietf.org/rfc/rfc2445.txt)

Very complete language to express recurrences, has features like up to some time,
every x units for n ocurrences and every n years.

The `tick.ical` namespace deals with these, but I don't know how complete it is.
There's also an [ical4j](https://github.com/ical4j).

## License

Copyright © 2019 Aleksander Madland Stapnes

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
