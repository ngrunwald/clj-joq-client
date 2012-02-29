(ns clj-joq-client.core
  (:import [java.io ByteArrayOutputStream]
           [java.net Socket]
           [java.nio ByteBuffer])
  (:use [slingshot.slingshot])
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]))

(def ^{:dynamic true} *client*)

(defrecord JoqClient [socket is os])

(defn connected?
  [{:keys [socket]}]
  (and (.isConnected socket)
       (not (and (.isInputShutdown socket)
                 (.isOutputShutdown socket)))))

(defn read-joq-line
  [{:keys [is] :as client}]
  (let [baos (ByteArrayOutputStream.)]
    (loop [begin true]
      (let [val (.read is)]
        (cond
          (= -1 val) (throw+ {:type ::parse-error})
          (= 10 val) (.write baos val)
          (and (= 62 val) begin) (.write baos val)
          :else (do (.write baos val) (recur false)))))
    (String. (.toByteArray baos))))

(defn read-joq-message*
  [client]
  (str/join
   ""
   (loop [lines []]
     (let [line (read-joq-line client)]
       (if (= line ">")
         lines
         (recur (conj lines line)))))))

(defn read-joq-message
  [client]
  (json/parse-string (read-joq-message* client) true))

(defn send-joq-command*
  [{:keys [os] :as client} cmd]
  (let [cmd-bytes (.getBytes cmd)
        cmd-length (alength cmd-bytes)
        prefix-bytes (.getBytes (str "<" cmd-length ">"))
        prefix-length (alength prefix-bytes)
        bb (ByteBuffer/allocate (+ cmd-length prefix-length))]
    (doto bb
      (.put prefix-bytes)
      (.put cmd-bytes))
    (.write os (.array bb)))
  (read-joq-message* client))

(defn create-joq-client
  [& {:keys [host port timeout]
      :or {host "localhost"
           port 1970
           timeout 10000}}]
  (let [socket (Socket. host port)]
    (try+
     (doto socket
       (.setSoTimeout timeout)
       (.setSoLinger false 0)
       (.setTcpNoDelay true))
     (let [client (JoqClient. socket
                              (.getInputStream socket)
                              (.getOutputStream socket))]
       (read-joq-message* client)
       (send-joq-command* client "mode json")
       client)
     (catch Object _
       (log/error (:throwable &throw-context) "error creating joq client")
       (.close socket)))))

(defn close-joq-client
  [client]
  (doseq [o (vals (select-keys client [:socket :os :is]))]
    (.close o))
  client)

(defmulti joq-cmd
  (fn [& args]
    (= (type (first args)) clj_joq_client.core.JoqClient)))

(defmethod joq-cmd true
  [client cmd & args]
  (let [words (concat [(name cmd)] args)
        command (str (str/join " " words))
        msg (send-joq-command* client command)]
    (json/parse-string msg true)))

(defmethod joq-cmd false
  [& args]
  (apply joq-cmd *client* args))

(defmacro with-joq-client
  [client & body]
  `(binding [clj-joq-client.core/*client* ~client]
     ~@body))

(defmacro with-open-joq-client
  [conf & body]
  `(let [client# (apply clj-joq-client.core/create-joq-client ~conf)]
     (try
       (clj-joq-client.core/with-joq-client client#
         ~@body)
       (finally
        (clj-joq-client.core/close-joq-client client#)))))