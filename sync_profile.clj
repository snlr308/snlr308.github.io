#!/usr/bin/env bb

(require '[clojure.java.io :as io]
         '[cheshire.core :as json]
         '[babashka.http-client :as http]
         '[babashka.process :refer [shell]])

;; --- CONFIGURATION ---
;; GitHub Actions automatically provides the actor, but we use your secret token
(def github-username (or (System/getenv "GH_USERNAME") "snlr308"))
(def github-token    (System/getenv "GH_TOKEN"))
(def readme-path     "README.md")

(def headers {"Authorization" (str "Bearer " github-token)
              "Accept" "application/vnd.github.v3+json"})

;; --- LOGIC ---

(defn fetch-all-repos []
  (println "🔍 Querying GitHub API for metadata...")
  (let [url (str "https://api.github.com/user/repos?per_page=100&sort=updated&type=owner")]
    (-> (http/get url {:headers headers})
        :body
        (json/parse-string true))))

(defn format-repo-row [repo]
  (let [name     (get repo :name)
        private? (get repo :private)
        desc     (or (get repo :description) "_No description provided._")
        lang     (or (get repo :language) "Nix/Lisp")
        status   (if private? "🔒" "🌍")]
    (str "| " status " **" name "** | " desc " | `" lang "` |")))

(defn generate-markdown-table [repos]
  (let [header "| S | Project Name & Description | Tech |\n| :--- | :--- | :--- |\n"
        rows (->> repos
                  (remove #(= (:name %) github-username)) ;; Hide the profile repo itself
                  (map format-repo-row)
                  (clojure.string/join "\n"))]
    (str "### 🛠️ Systems & Orchestration Catalog\n\n" 
         header rows "\n\n"
         "*Last automated sync: " (str (java.time.LocalDate/now)) " (GitHub Actions)*")))

(defn sync! []
  (if-not github-token
    (do (println "❌ GH_TOKEN not found in environment!") (System/exit 1))
    (let [repos (fetch-all-repos)
          table-md (generate-markdown-table repos)
          current-content (slurp readme-path)
          start-marker "<!-- PROJECTS_START -->"
          end-marker   "<!-- PROJECTS_END -->"]
      
      (if (clojure.string/includes? current-content start-marker)
        (let [pattern (re-pattern (str "(?s)" start-marker ".*" end-marker))
              replacement (str start-marker "\n" table-md "\n" end-marker)
              new-content (clojure.string/replace current-content pattern replacement)]
          
          (spit readme-path new-content)
          
          (shell "git" "config" "user.name" "github-actions[bot]")
          (shell "git" "config" "user.email" "github-actions[bot]@users.noreply.github.com")
          (shell "git" "add" "README.md")
          
          (let [status (shell {:out :string} "git" "status" "--porcelain")]
            (if (clojure.string/blank? (:out status))
              (println "Checking... No changes to project catalog.")

(let [remote-url (str "https://x-access-token:" github-token "@github.com/" github-username "/snlr308.git")]
  (shell "git" "commit" "-m" "chore: automated project catalog sync")
  (shell {:sensitive true} "git" "push" "--force" remote-url "main")
  (println "🚀 Changes pushed successfully!")))))

        ;; This else belongs to the (if (clojure.string/includes? ...))
        (println "❌ Error: Could not find markers in README.md")))))

(sync!)