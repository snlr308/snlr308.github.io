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

;; Organizations to include in the project catalog
(def org-names (let [env-orgs (System/getenv "GH_ORGS")]
                 (if (and env-orgs (not (clojure.string/blank? env-orgs)))
                   (clojure.string/split env-orgs #",")
                   ["V-You"])))

(def headers {"Authorization" (str "Bearer " github-token)
              "Accept" "application/vnd.github.v3+json"})

;; --- LOGIC ---

(defn fetch-all-repos []
  (println "Querying GitHub API for personal repos...")
  (let [url (str "https://api.github.com/user/repos?per_page=100&sort=updated&type=owner")]
    (-> (http/get url {:headers headers})
        :body
        (json/parse-string true))))

(defn fetch-org-repos [org-name]
  (println (str "Querying GitHub API for " org-name " org repos..."))
  (let [url (str "https://api.github.com/orgs/" org-name "/repos?per_page=100&sort=updated")]
    (->> (-> (http/get url {:headers headers})
             :body
             (json/parse-string true))
         (map #(assoc % :org-name org-name)))))

(defn format-repo-row [repo]
  (let [name     (get repo :name)
        org      (get repo :org-name)
        private? (get repo :private)
        desc     (or (get repo :description) "-")
        lang     (or (get repo :language) "Nix/Lisp")
        display  (if org
                   (str org "/" name)
                   name)]
;;    (str "| " status " **" name "** | " desc " | `" lang "` |")))
    (str "| **" display "** | " desc " | `" lang "` |")))

(defn generate-markdown-table [repos]
  (let [header "| Repo | Description |  |\n| :--- | :--- | :--- |\n"
        rows (->> repos
                  (remove #(= (:name %) github-username)) ;; Hide the profile repo itself
                  (map format-repo-row)
                  (clojure.string/join "\n"))]
    (str "# Projects\n\n" 
         header rows "\n\n"
         "*Last automated sync: " (str (java.time.LocalDate/now)) " (GitHub Actions)*")))

(defn sync! []
  (if-not github-token
    (do (println "GH_TOKEN not found in environment!") (System/exit 1))
    (let [personal-repos (fetch-all-repos)
          org-repos      (mapcat fetch-org-repos org-names)
          all-repos      (concat personal-repos org-repos)
          table-md (generate-markdown-table all-repos)
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

(let [profile-repo-url (str "https://x-access-token:" github-token "@github.com/" github-username "/" github-username ".git")
      website-repo-url (str "https://x-access-token:" github-token "@github.com/" github-username "/" github-username ".github.io.git")]
  
  ;; Remove the credential helper injected by actions/checkout, which overrides our PAT
  (shell "git" "config" "--local" "--unset-all" "http.https://github.com/.extraheader")
  
  (shell "git" "commit" "-m" "chore: automated project catalog sync")
  
  (println "Syncing Profile README...")
  ;; Force-push required: pushing this repo's history into a different repo (snlr308/snlr308)
  (shell {:sensitive true} "git" "push" "--force" profile-repo-url "main")
  
  (println "Syncing Website...")
  ;; Normal push: same repo, keeps history compatible with local clones
  (shell {:sensitive true} "git" "push" website-repo-url "main")
  
  (println "Full Sync Complete!")))))

        ;; This else belongs to the (if (clojure.string/includes? ...))
        (println "Error: Could not find markers in README.md")))))

(sync!)