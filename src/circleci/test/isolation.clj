(ns circleci.test.isolation
  (:import (java.lang SecurityManager)))

(defn- isolating-security-manager [deny?]
  (proxy [SecurityManager] []
    (checkPermission
      ([^java.security.Permission p]
       (when (deny? p)
         (throw (SecurityException. (str "Isolated tests cannot " p)))))
      ([^java.security.Permission p _object]
       (when (deny? p)
         (throw (SecurityException. (str "Isolated tests cannot " p))))))))

(defn- run-isolated [f deny?]
  (let [original-security-manager (System/getSecurityManager)]
    (try
      (System/setSecurityManager (isolating-security-manager deny?))
      (f)
      (finally
        (System/setSecurityManager original-security-manager)))))

(defn default-deny?
  "Should this permission be blocked from unit tests?"
  [p]
  (if (instance? java.io.FilePermission p)
    ;; You're looking at this and thinking, "wait, why do we allow access
    ;; to reading files inside the Java home?" which to be honest is a really
    ;; good question! The sad fact of life is that sometimes pure functions
    ;; still require disk access. For instance, the first time you calculate
    ;; the checksum of a bunch of bytes, it must lazy-load the bytecode for
    ;; the relevant MessageDigest implementation from disk.
    (not (or (re-find #"/dev/u?random" (.getName p))
             (.startsWith (.getName p) (System/getProperty "java.home"))))
    (#{java.net.NetPermission
       java.net.SocketPermission
       java.net.URLPermission} (class p))))

(defn enforce
  "Ensure unit tests running in this fixture do not do I/O.

  By default, tests are considered unit tests unless they are tagged with
  ^:integration or ^:io, but you can provide your own selectors. You can
  also provide your own deny? predicate which takes an instance of
  java.security.Permission and tells whether it should be blocked or not."
  ([] (enforce [:integration :io] default-deny?))
  ([selectors] (enforce selectors default-deny?))
  ([selectors deny?]
   (fn [f]
     (if (some selectors (meta f))
       (f)
       (run-isolated f deny?)))))
