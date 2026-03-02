#!/usr/bin/env bb

;; (?s) in the regex causes Babashka to treat the entire README as one single string, including newlines 
;; It "scoop out" everything between the markers and drops in the overview table
;; Overview table is built from title and description of all repos under snlr308
;; Marker: `` and ``


(require '[clojure.java.io :as io]
         '[cheshire.core :as json]
         '[babashka.http-client :as http]
         '[babashka.process :refer [shell]])

;; --- CONFIGURATION ---
(def github-username "snlr308")
(def github-token "token") 
(def profile-repo "snlr308")

;; Organizations to include in the project catalog
(def org-names ["V-You"])

(def headers {"Authorization" (str "Bearer " github-token)
              "Accept" "application/vnd.github.v3+json"})

;; --- LOGIC ---

(defn fetch-all-repos []
  (println "🔍 Querying GitHub API for personal repos...")
  ;; 'type=owner' ensures we don't list forks or repos you've contributed to but don't own
  (let [url (str "https://api.github.com/user/repos?per_page=100&sort=updated&type=owner")]
    (-> (http/get url {:headers headers})
        :body
        (json/parse-string true))))

(defn fetch-org-repos [org-name]
  (println (str "🔍 Querying GitHub API for " org-name " org repos..."))
  (let [url (str "https://api.github.com/orgs/" org-name "/repos?per_page=100&sort=updated")]
    (->> (-> (http/get url {:headers headers})
             :body
             (json/parse-string true))
         (map #(assoc % :org-name org-name)))))

(defn format-repo-row [repo]
  (let [name     (get repo :name)
        org      (get repo :org-name)
        private? (get repo :private)
        desc     (or (get repo :description) "_No description provided._")
        lang     (or (get repo :language) "Nix/Lisp")
        status   (if private? "🔒" "🌍")
        display  (if org
                   (str org "/" name)
                   name)]
    (str "| " status " **" display "** | " desc " | `" lang "` |")))

(defn generate-markdown-table [repos]
  (let [header "| S | Project Name & Description | Tech |\n| :--- | :--- | :--- |\n"
        rows (->> repos
                  (remove #(= (:name %) profile-repo)) 
                  (map format-repo-row)
                  (clojure.string/join "\n"))]
    (str "### 🛠️ Systems & Orchestration Catalog\n\n" 
         header rows "\n\n"
         "*Last automated sync: " (str (java.time.LocalDate/now)) "*")))

(defn update-profile-readme! []
  (let [personal-repos (fetch-all-repos)
        org-repos      (mapcat fetch-org-repos org-names)
        all-repos      (concat personal-repos org-repos)
        table-md (generate-markdown-table all-repos)]
    
    (println "📦 Cloning profile README repository...")
    (shell "rm -rf temp_profile")
    (shell "git clone" (str "https://" github-username ":" github-token "@github.com/" github-username "/" profile-repo ".git") "temp_profile")
    
    (let [readme-path "temp_profile/README.md"
          current-content (slurp readme-path)
          ;; These must match exactly what you pasted into the README
          start-marker ""
          end-marker   ""]
      
      (if (clojure.string/includes? current-content start-marker)
        (let [pattern (re-pattern (str "(?s)" start-marker ".*" end-marker))
              replacement (str start-marker "\n" table-md "\n" end-marker)
              new-content (clojure.string/replace current-content pattern replacement)]
          
          (spit readme-path new-content)
          (println "🚀 Syncing updates to GitHub Profile...")
          (shell {:dir "temp_profile"} "git add .")
          (shell {:dir "temp_profile"} "git commit -m" "Refreshed project catalog (Babashka sync)")
          (shell {:dir "temp_profile"} "git push")
          (shell "rm -rf temp_profile")
          (println "✅ Portfolio updated successfully!"))
        
        (do
          (println "❌ Error: Could not find markers in README.md")
          (println "Make sure your README contains:")
          (println start-marker)
          (println end-marker))))))

(update-profile-readme!)