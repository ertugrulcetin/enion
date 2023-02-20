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
(def project-resources (str "/Users/ertugrulcetin/IdeaProjects/" project-name "/enion-cljs/resources"))
(def project-dir (str project-resources "/public"))
(def project-unzip-dir (str project-dir ".zip"))
(def vendor-dir "/Users/ertugrulcetin/IdeaProjects/enion/enion-cljs/src/enion_cljs/vendor")
(def sleep-timeout 5000)

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
(tasks/shell {:dir project-dir} (str "rm -r files"))
(tasks/shell {:dir project-dir} (str "unzip -o " project-unzip-dir))

(tasks/shell {:dir project-resources} (str "rm public.zip"))
(tasks/shell {:dir project-dir} (str "cp logo2.png logo.png"))
(tasks/shell {:dir project-dir} (str "rm playcanvas-stable.min.js"))
(tasks/shell {:dir project-dir} (str "touch " vendor-dir "/all.js"))

(def index-html-path (str project-dir "/index.html"))
(def index-html (slurp index-html-path))

(with-open [w (io/writer index-html-path)]
  (-> index-html
      (str/replace #"<title>enion</title>" "<title>Enion Online</title>")
      (str/replace #"</head>" "<script src=\"https://game-cdn.poki.com/scripts/v2/poki-sdk.js\"></script>\n</head>")
      (str/replace #"<script src=\"playcanvas-stable.min.js\"></script>" "")
      (str/replace #"<script src=\"__settings__.js\"></script>" "")
      (str/replace #"<script src=\"__modules__.js\"></script>" "")
      (str/replace #"<script src=\"__start__.js\"></script>" "")
      (str/replace #"<script src=\"__loading__.js\"></script>" "")
      (str/replace #"<body>" "<body>\n   <script src=\"js/compiled/app.js\"></script>")
      (str/replace #"<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\">"
                   "<link rel=\"stylesheet\" type=\"text/css\" href=\"styles.css\">\n
<link rel=\"stylesheet\" type=\"text/css\" href=\"https://cdnjs.cloudflare.com/ajax/libs/intro.js/6.0.0/introjs.min.css\">\n
<link rel=\"stylesheet\" type=\"text/css\" href=\"introjs.css\">")
      (str/replace #"</body>"
                   "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/intro.js/6.0.0/intro.min.js\"></script>\n</body>")
      (#(.write w %))))

(with-open [w (io/writer (str vendor-dir "/all.js"))]
  (let [settings (slurp (str project-dir "/__settings__.js"))
        modules (slurp (str project-dir "/__modules__.js"))
        start (slurp (str project-dir "/__start__.js"))
        loading (slurp (str project-dir "/__loading__.js"))]
    (.write w (str settings "\n"
                   modules "\n"
                   start "\n"
                   loading))))

(with-open [w (io/writer (str project-dir "/styles.css"))]
  (.write w "html {
    height: 100%;
    width: 100%;
    background-color: white;
}
body {
    margin: 0;
    background-color: white;
    font-family: IMMORTAL, arial, sans-serif;
    overflow: hidden;
}

@font-face {
    font-family: 'IMMORTAL';
    src: url('IMMORTAL.ttf') format('truetype');
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

\n.switch {\n  position: relative;\n  display: inline-block;\n  width: 60px;\n  height: 34px;\n}\n\n.switch input {\n  opacity: 0;\n  width: 0;\n  height: 0;\n}\n\n.slider {\n  position: absolute;\n  cursor: pointer;\n  top: 0;\n  left: 0;\n  right: 0;\n  bottom: 0;\n  background-color: #ccc;\n  -webkit-transition: .4s;\n  transition: .4s;\n}\n\n.slider:before {\n  position: absolute;\n  content: \" \";\n  height: 26px;\n  width: 26px;\n  left: 4px;\n  bottom: 4px;\n  background-color: white;\n  -webkit-transition: .4s;\n  transition: .4s;\n}\n\ninput:checked + .slider {\n  background-color: #2196F3;\n}\n\ninput:focus + .slider {\n  box-shadow: 0 0 1px #2196F3;\n}\n\ninput:checked + .slider:before {\n  -webkit-transform: translateX(26px);\n  -ms-transform: translateX(26px);\n  transform: translateX(26px);\n}\n\n/* Rounded sliders */\n.slider.round {\n  border-radius: 34px;\n}\n\n.slider.round:before {\n  border-radius: 50%;\n}

"))

;; Usage
;; bb ./download-game.clj
