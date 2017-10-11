(ns ci3.telegram
  (:require
    [environ.core :refer [env]]
    [ci3.k8s :as k8s]
    [morse.handlers :as h]
    [morse.polling :as p]
    [morse.api :as t]
    [clojure.string :as str]))

(def token  (or (env :telegram-token)
                (k8s/secret :telegram :token)))
(def chatid (or (env :chatid)
                (k8s/secret :telegram :chatid)))

(def channel (atom {}))

(h/defhandler handler

  (h/command-fn "start"
    (fn [{{id :id :as chat} :chat}]
      (t/send-text token id " Welcome to CI3!")))

  (h/command-fn "chatid"
    (fn [{{id :id :as chat} :chat}]
      (t/send-text token id (str "Chat-id: " id))))

  (h/command-fn "help"
    (fn [{{id :id :as chat} :chat}]
      (t/send-text token id "/chatid - show current chatid ")))

  (h/message-fn
    (fn [{{id :id} :chat :as message}]
      (t/send-text token id "I don't do a whole lot ... yet."))))

(defn notify [msg]
  (t/send-text token chatid {:parse_mode "Markdown"} msg))

(defn start []
  (if (str/blank? token)
    (println "Please provde token in TELEGRAM_TOKEN environment variable!")
    (reset! channel (p/start token handler))) )

(defn stop []
  (when @channel
    (p/stop @channel)
    (reset! channel nil)))

(defn restart []
  (stop)
  (start))

(comment
  (start)
  (stop)
  (restart))
