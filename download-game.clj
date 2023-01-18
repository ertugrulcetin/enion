#!/usr/bin/env bb

;; To install babashka -> https://github.com/babashka/babashka#quickstart

(require '[babashka.curl :as curl])
(require '[babashka.tasks :as tasks])
(require '[cheshire.core :as json])
(require '[clojure.java.io :as io])
(require '[clojure.string :as str])

(def project-id 915833)
(def scene-ids [1396021])
(def api-token "J075L9lLsPT9N0Hqls1msBLsSsYILxH5")
(def project-name "enion")
(def project-dir (str "/Users/ertugrulcetin/IdeaProjects/" project-name))
(def project-unzip-dir (str project-dir ".zip"))
(def sleep-timeout 1000)

(def asset-id 118396797)
(def empty-app-js-file-path "/Users/ertugrulcetin/IdeaProjects/enion/enion-cljs/app.js")

(println "Uploading empty app.js")


(defn upload-empty-app-js
  []
  (curl/put (str "https://playcanvas.com/api/assets/" asset-id)
            {:headers {"Authorization" (str "Bearer " api-token)}
             :raw-args ["-F" (str "file=@" empty-app-js-file-path)]}))


(upload-empty-app-js)

(println "Done - Uploading empty app.js")

(println "Preparing download...")


(def job-id
  (-> (curl/post "https://playcanvas.com/api/apps/download"
                 {:headers {"accept" "application/json"
                            "content-type" "application/json"
                            "authorization" (str "Bearer " api-token)}
                  :body (json/generate-string {:project_id project-id
                                               :scenes scene-ids
                                               :name project-name
                                               :optimize_scene_format true
                                               :scripts_minify false})})
      :body
      json/parse-string
      (get "id")))


(println "Preparing download completed.")


(while (let [_ (Thread/sleep sleep-timeout)
             job-result (-> (curl/get (str "https://playcanvas.com/api/jobs/" job-id)
                                      {:headers {"accept" "application/json"
                                                 "content-type" "application/json"
                                                 "authorization" (str "Bearer " api-token)}})
                            :body
                            json/parse-string)
             job-status (get job-result "status")]
         (case job-status
           "running" true
           "error" (throw (ex-info "Error happened during fetching job status" {}))
           (do
             (io/copy
               (:body (curl/get (get-in job-result ["data" "download_url"])
                                {:as :bytes}))
               (io/file project-unzip-dir))
             false)))
  (println "Fetching job status...")
  (Thread/sleep sleep-timeout))


(tasks/shell (str "mkdir -p " project-dir))
(tasks/shell {:dir project-dir} (str "unzip -o " project-unzip-dir))

(def index-html (slurp "index.html"))


(with-open [w (io/writer  "index.html")]
  (->  index-html
       (str/replace  #"<script src=\"__loading__.js\"></script>"
                     "<script src=\"__loading__.js\"></script>\n    <script src=\"enion-cljs/resources/public/js/compiled/app.js\"></script>")
       (#(.write w %))))


(with-open [w (io/writer "styles.css")]
  (.write w "html {
    height: 100%;
    width: 100%;
    background-color: white;
}
body {
    margin: 0;
    background-color: white;
    font-family: Comic Papyrus, arial, sans-serif;
    overflow: hidden;
}

@font-face {
    font-family: 'Comic Papyrus';
    src: url('Comic Papyrus.ttf') format('truetype');
}

#app {
	height: 100%;
 width: 100%;
}

#application-canvas {
    display: block;
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
}
#application-canvas.fill-mode-NONE {
    margin: auto;
}
#application-canvas.fill-mode-KEEP_ASPECT {
    width: 100%;
    height: auto;
    margin: 0;
}
#application-canvas.fill-mode-FILL_WINDOW {
    width: 100%;
    height: 100%;
    margin: 0;
}

canvas:focus {
    outline: none;
}


@property --cooldown {
  syntax: \"<percentage>\";
  inherits: false;
  initial-value: 0%;
}
"))


;; Usage
;; bb ./download-game.clj
