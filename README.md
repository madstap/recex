# recex

A recurrence expression, or a recex, is a domain specific language to
express an infinite series of recurring times.

## Project maturity

Still figuring out the api, breaking changes are likely.

## Usage

I haven't deployed to clojars yet, but you can depend on the library with `deps.edn`.

```clojure
madstap/recex {:git/url "https://github.com/madstap/recex.git"
               :sha "d9ce4ae0438650bc56525495f7236f12bf6d1f89"}
```

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

A recex a vector with slots:

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

TODO: Time expressions.

### Clojure API

Require the namespace:

```clojure
(ns my.app
  (:require
   [madstap.recex :as recex]
   [tick.alpha.api :as t]))
```

The `recex/time` function generates an infinite sequence of times from a recex.
The times are either zoned-date-time or offset-date-time depending on whether a
time zone or an offset was specified.

It takes a time `now` which can be either an instant, zoned-date-time
or offset-date-time, (ie anything that represents an instant in time) and a recex.

`now` can be omitted, in which case it will use `t/now`, but it's
not recommended to use the implicit now except for experimenting at the repl.
Omitting the `now` argument makes the function impure, and thus trickier to test.

(Remember to `take` when experimenting at the repl, it is an infinite sequence.)

```Clojure
(take 2 (recex/times (t/now) ["00:00"]))
;; => (#time/zoned-date-time "2019-09-06T00:00Z[UTC]"
;;     #time/zoned-date-time "2019-09-07T00:00Z[UTC]")

(take 2 (recex/times ["00:00"]))
;; => (#time/zoned-date-time "2019-09-06T00:00Z[UTC]"
;;     #time/zoned-date-time "2019-09-07T00:00Z[UTC]")
```

### Chime

To use recex together with chime it's necessary to translate
`java.time.ZonedDateTime`s to joda time, because chime was written
before java.time was a thing.

(Add [clj-time](https://github.com/clj-time/clj-time) as a dependency.)

```clojure
(require '[clj-time.coerce :as ct.coerce])

(defn zdt->joda-time [zdt]
  (-> zdt .toInstant java.util.Date/from ct.coerce/from-date))
```

(Maybe someone could port chime to java.time)

## Caveats/warnings

### Impossible combinations

Specifying an impossible recex is not considered valid, but currently
this is not checked. For example:

```clojure
;; The last friday of the month that is also the 1st day of the month.
[[-1 #time/day-of-week "FRIDAY"] 1 #time/time "12:00"]
```

This is an impossible combination, and the current behavior is an infinite loop.
If you accept recexes from untrusted sources,
you're gonna have a bad time (denial of service attacks).

You could probably mitigate this with a timeout.

Doing some math to validate whether a recex is impossible should be doable,
but may be non-trivial. PRs welcome.

## Prior art

### cron

cron is the de-facto standard time dsl. It is text-based, and not very
readable (YMMV). Time zones are not part of the expression itself, it
uses the system time zone or you can choose the time zone with config files
or env vars, which is quite fiddly.

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
