#!/usr/bin/env bb
(ns deploy
  (:require
   [rewrite-clj.zip :as zip]
   [clojure.string :as str]
   [babashka.process :as proc]))

(defn on-main? []
  (-> (proc/sh ["git" "branch" "--show-current"])
      :out
      (str/trim)
      ;; TODO: Move to main
      #{"main" "master"}))

(defn clean-workdir? []
  (-> (proc/sh ["git" "status" "--porcelain"])
      :out
      str/blank?))

(defn update-version [version]
  (let [new-deps-edn (-> (zip/of-string (slurp "deps.edn"))
                         (zip/get :aliases)
                         (zip/get :jar)
                         (zip/get :exec-args)
                         (zip/assoc :version version)
                         (zip/root-string))]
    (spit "deps.edn" new-deps-edn)))
