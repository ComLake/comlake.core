;;;; HTTP API functional tests
;;;; Copyright (C) 2014-2017  Zach Tellman
;;;; Copyright (C) 2021  Nguyễn Gia Phong
;;;;
;;;; This file is part of comlake-core.
;;;;
;;;; comlake-core is free software: you can redistribute it and/or modify
;;;; it under the terms of the GNU Affero General Public License version 3
;;;; as published by the Free Software Foundation.
;;;;
;;;; comlake-core is distributed in the hope that it will be useful,
;;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;;; GNU Affero General Public License for more details.
;;;;
;;;; You should have received a copy of the GNU Affero General Public License
;;;; along with comlake-core.  If not, see <https://www.gnu.org/licenses/>.

(ns comlake-core.api-test
  "HTTP API functional tests."
  (:require [aleph.http :as http]
            [aleph.netty :refer [wait-for-close]]
            [clojure.data.json :as json]
            [clojure.test :refer :all]
            [clojure.java.io :refer [file input-stream reader resource]]
            [comlake-core.main :refer [route]]
            [comlake-core.rethink :as rethink]))

(def port 42069)
(def json-body (comp json/read reader :body))

(defn make-url
  "Make URL for given endpoint."
  [endpoint]
  (str "http://localhost:" port endpoint))

(defmacro with-server
  "Manage a test server for the code in body."
  [& body]
  `(let [server# (http/start-server route {:port port})]
     ;; TODO: add mock data
     (rethink/clear rethink/table)
     (try ~@body
          (finally (.close server#)
                   (wait-for-close server#)))))

(deftest http-add
  (let [url (make-url "/add")
        resrc (resource "test/Interjection")
        headers {:accept "application/json"
                 :content-length (.length (file resrc))
                 :content-type "text/plain"
                 :x-comlake-name "Interjection"
                 :x-comlake-source "https://wiki.installgentoo.com"
                 :x-comlake-topics ["Natural language" "copypasta"]
                 :x-comlake-language "English"}]
    (with-server
      (testing "success"
        (with-open [stream (input-stream resrc)]
          (let [response @(http/post url {:headers headers :body stream})]
            (is (and (= 200 (:status response))
                     (= "QmbwXK2Wg6npoAusr9MkSduuAViS6dxEQBNzqoixanVtj5"
                        (-> response json-body (get "cid"))))))))
      (testing "missing headers"
        (with-open [stream (input-stream resrc)]
          (let [options {:headers (dissoc headers :x-comlake-source)
                         :body stream
                         :throw-exceptions? false}
                response @(http/post url options)]
            (is (and (= 400 (:status response))
                     (= "missing metadata fields"
                        (get (json-body response) "error"))))))))))

(deftest http-find
  (let [url (make-url "/find")
        options {:accept :json
                 :content-type :json
                 :body (json/write-str ["<" ["." "length"] 0])}]
    (with-server
      (testing "success"
        (let [response @(http/post url options)]
          (is (and (= 200 (:status response))
                   ;; Obviously length cannot be negative.
                   (empty? (json-body response))))))
      (testing "malformed query"
        (let [override {:body (json/write-str ["8=D" "foo" "bar"])
                        :throw-exceptions? false}
              response @(http/post url (merge options override))]
          (is (and (= 400 (:status response))
                   (= "malformed query"
                      (get (json-body response) "error")))))))))
