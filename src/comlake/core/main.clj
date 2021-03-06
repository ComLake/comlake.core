;;;; Entry point
;;;; Copyright (C) 2021  Nguyễn Gia Phong
;;;;
;;;; This file is part of comlake.core.
;;;;
;;;; comlake.core is free software: you can redistribute it and/or modify
;;;; it under the terms of the GNU Affero General Public License version 3
;;;; as published by the Free Software Foundation.
;;;;
;;;; comlake.core is distributed in the hope that it will be useful,
;;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;;; GNU Affero General Public License for more details.
;;;;
;;;; You should have received a copy of the GNU Affero General Public License
;;;; along with comlake.core.  If not, see <https://www.gnu.org/licenses/>.

(ns comlake.core.main
  "Entry point."
  (:gen-class)
  (:require [aleph.http :refer [start-server]]
            [clojure.string :refer [starts-with?]]
            [taoensso.timbre :refer [debug]])
  (:import (comlake.core Configuration HttpHandler)
           (comlake.core.fs InterPlanetaryFileSystem)
           (comlake.core.db PostgreSQL)))

(defn route
  "Route HTTP endpoints."
  [request handler]
  (let [method (:request-method request)
        uri (:uri request)]
    (cond
      (and (= method :post) (= uri "/dir")) (.mkdir handler)
      (and (= method :post) (= uri "/file")) (.save handler (:headers request)
                                                            (:body request))
      (and (= method :post) (= uri "/cp")) (.cp handler (:body request))
      (and (= method :post) (= uri "/dataset")) (.add handler (:body request))
      (and (= method :post) (= uri "/update")) (.update handler (:body request))
      (and (= method :post) (= uri "/find")) (.find handler (:body request))
      (and (= method :get)
           (starts-with? uri "/dir/")) (.ls handler (subs uri 5))
      (and (= method :get)
           (starts-with? uri "/file/")) (.get handler (subs uri 6))
      (and (= method :get)
           (starts-with? uri "/schema/")) (.schema handler (subs uri 8))
      (and (= method :post)
           (starts-with? uri "/extract/")) (.extract handler (subs uri 9)
                                                             (:body request))
      :else (HttpHandler/error "unsupported" 404))))

(defn make-handler
  "Construct a Ring request handler."
  [fs db]
  (let [handler (HttpHandler. fs db)]
    (fn [request]
      ;; java.util.Map.of does not produce clojure map.
      (let [response (reduce (fn [m [k v]] (assoc m k v)) {}
                             (route request handler))]
        (debug request "=>" response)
        response))))

(defn -main
  "Start the HTTP server."
  ([] (-main "8090"))
  ([port & args]
   (let [cfg (Configuration.)
         fs (InterPlanetaryFileSystem. (.-ipfsMultiAddr cfg))
         db (PostgreSQL. (.-psqlUrl cfg) (.-psqlUser cfg) (.-psqlPasswd cfg))]
    (start-server (make-handler fs db) {:port (Integer/parseInt port)}))))
