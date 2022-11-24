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
       (str/replace  #"<script src=\"app.js\"></script>" "")
       (str/replace  #"<script src=\"__loading__.js\"></script>"
                     "<script src=\"__loading__.js\"></script>\n\t
                       <script src=\"enion-cljs/resources/public/js/compiled/app.js\"></script>")
       (#(.write w %))))


;; Usage
;; bb ./download-game.clj
