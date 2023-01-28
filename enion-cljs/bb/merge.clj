#!/usr/bin/env bb

(require '[babashka.tasks :as tasks])
(require '[clojure.java.io :as io])
(require '[clojure.string :as str])

(def project-name "enion")
(def project-resources (str "/Users/ertugrulcetin/IdeaProjects/" project-name "/enion-cljs/resources"))
(def project-dir (str project-resources "/public"))
(def vendor-dir "/Users/ertugrulcetin/IdeaProjects/enion/enion-cljs/src/enion_cljs/vendor")

(with-open [w (io/writer (str vendor-dir "/all.js"))]
  (let [settings (slurp (str project-dir "/__settings__.js"))
        modules (slurp (str project-dir "/__modules__.js"))
        start (slurp (str project-dir "/__start__.js"))
        loading (slurp (str project-dir "/__loading__.js"))]
    (.write w (str settings "\n"
                   modules "\n"
                   start "\n"
                   loading))))
