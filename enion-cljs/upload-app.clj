#!/usr/bin/env bb
(require '[babashka.curl :as curl])

(def api-token "J075L9lLsPT9N0Hqls1msBLsSsYILxH5")
(def asset-id 110757119)
(def app-file-path "/Users/ertugrulcetin/IdeaProjects/enion/enion-cljs/resources/public/js/compiled/app.js")

(defn upload-app []
  (curl/put (str "https://playcanvas.com/api/assets/" asset-id)
             {:headers {"Authorization" (str "Bearer " api-token)}
              :raw-args ["-F" (str "file=@" app-file-path)]}))

(upload-app)

;; Usage
;; bb ./upload-app.clj
