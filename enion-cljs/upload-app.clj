#!/usr/bin/env bb
(require '[babashka.curl :as curl])
(require '[babashka.tasks :as tasks])

(def api-token "J075L9lLsPT9N0Hqls1msBLsSsYILxH5")
(def asset-id 118396797)
(def app-file-path "/Users/ertugrulcetin/IdeaProjects/enion/enion-cljs/resources/public/js/compiled/app.js")
(def project-dir "/Users/ertugrulcetin/IdeaProjects/enion/enion-cljs")

(defn upload-app []
  (curl/put (str "https://playcanvas.com/api/assets/" asset-id)
            {:headers {"Authorization" (str "Bearer " api-token)}
             :raw-args ["-F" (str "file=@" app-file-path)]}))

(tasks/shell {:dir project-dir} "npm run release")

(upload-app)

;; Usage
;; bb ./upload-app.clj
