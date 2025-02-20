(ns resilience.breaker
    (:refer-clojure :exclude [name reset!])
    (:require [resilience.util :as u]
            [resilience.spec :as s])
    (:import (io.github.resilience4j.circuitbreaker CircuitBreakerConfig CircuitBreakerConfig$Builder
               CircuitBreakerRegistry CircuitBreaker CircuitBreaker$StateTransition CircuitBreaker$Metrics CircuitBreakerConfig$SlidingWindowType)
      (java.time Duration)
      (java.util.function Predicate)
             (io.github.resilience4j.core EventConsumer)
      (io.github.resilience4j.circuitbreaker.event CircuitBreakerOnSuccessEvent CircuitBreakerOnErrorEvent
                                                   CircuitBreakerOnIgnoredErrorEvent
                                                   CircuitBreakerOnStateTransitionEvent
                                                   CircuitBreakerEvent CircuitBreakerEvent$Type) (java.util.concurrent TimeUnit)))



(defn ^CircuitBreakerConfig circuit-breaker-config
  "Create a CircuitBreakerConfig.

  Allowed options are:
  * :failure-rate-threshold
    Configures the failure rate threshold in
    percentage above which the circuit breaker should trip open and start
    short-circuiting calls.
    Must be a float/double. Default value is 50%.

  * :slow-call-rate-threshold
    Configures a threshold in percentage. The CircuitBreaker considers a call
    as slow when the call duration is greater than :slow-call-duration-threshold.
    When the percentage of slow calls is equal or greater the threshold, the
    CircuitBreaker transitions to open and starts short-circuiting calls.
    The threshold must be greater than 0 and not greater than 100.
    Default value is 100 percentage which means that all recorded calls must be
    slower than :slow-call-duration-threshold.

  * :writable-stack-trace-enabled
    Enables writable stack traces. When set to false, Exception#getStackTrace()
    returns a zero length array.
    This may be used to reduce log spam when the circuit breaker is open as the
    cause of the exceptions is already known (the circuit breaker is short-circuiting calls).

  * :wait-millis-in-open-state
    Configures the wait duration which specifies how long the
    circuit breaker should stay open, before it switches to half
    open.
    Default value is 60 seconds.

  * :wait-interval-function-in-open-state
    Configures an interval function which controls how long the CircuitBreaker should stay
    open, before it switches to half open. The default interval function returns a fixed wait
    duration of 60 seconds.
    A custom interval function is useful if you need an exponential backoff algorithm.

  * :slow-call-threshold-in-millis
    Configures the duration threshold in millis seconds above which calls are considered as slow and
    increase the slow calls percentage.
    Default value is 60 seconds.

  * :permitted-number-of-calls-in-half-open-state
    Configures the number of permitted calls when the CircuitBreaker is half open.
    The size must be greater than 0. Default size is 10.

  * :permitted-number-of-calls-in-half-open-state
    Configures the size of the ring buffer when the circuit breaker
    is half open. The circuit breaker stores the success/failure
    status of the latest calls in a ring buffer. For example, if
    :permitted-number-of-calls-in-half-open-state is 10, then at least 10 calls
    must be evaluated, before the failure rate can be calculated. If
    only 9 calls have been evaluated the CircuitBreaker will not trip
    back to closed or open even if all 9 calls have failed.
    The size must be greater than 0. Default size is 10.

  * :sliding-window-size
    Configures the size of the ring buffer when the circuit breaker is
    closed. The circuit breaker stores the success/failure status of the
    latest calls in a ring buffer. For example, if
    :sliding-window-size is 100, then at least 100 calls
    must be evaluated, before the failure rate can be calculated. If
    only 99 calls have been evaluated the circuit breaker will not trip
    open even if all 99 calls have failed.
    The default size is 100.

  * :sliding-window-size
    Configures the size of the sliding window which is used to record the outcome
    of calls when the CircuitBreaker is closed.
    :sliding-window-size configures the size of the sliding window. Sliding window
     can either be count-based or time-based.
    If :sliding-window-size is :COUNT_BASED, the last :sliding-window-size calls are
    recorded and aggregated.
    If :sliding-window-size is :TIME_BASED, the calls of the last :sliding-window-size
    seconds are recorded and aggregated.
    The :sliding-window-size must be greater than 0.
    The :minimum-number-of-calls must be greater than 0.
    If the slidingWindowType is :COUNT_BASED, the :minimum-number-of-calls cannot be
    greater than :sliding-window-size.
    If the slidingWindowType is :TIME_BASED, you can pick whatever you want.
    Default slidingWindowSize is 100.

  * :minimum-number-of-calls
    Configures configures the minimum number of calls which are required
    (per sliding window period) before the CircuitBreaker can calculate the error rate.
    For example, if :minimum-number-of-calls is 10, then at least 10 calls must be
    recorded, before the failure rate can be calculated.
    If only 9 calls have been recorded the circuit breaker will not transition to open
    even if all 9 calls have failed.
    Default minimumNumberOfCalls is 100.

  * :sliding-window-type
    Configures the type of the sliding window which is used to record the outcome of
    calls when the circuit breaker is closed.
    Sliding window can either be count-based or time-based.
    If :sliding-window-type is :COUNT_BASED, the last :sliding-window-size calls are
    recorded and aggregated.
    If :sliding-window-type is :TIME_BASED, the calls of the last :sliding-window-size
    seconds are recorded and aggregated.
    Default slidingWindowType is :COUNT_BASED.

  * :record-failure
    Configures a function which take a `throwable` as argument and
    evaluates if an exception should be recorded as a failure and thus
    increase the failure rate.
    The predicate function must return true if the exception should
    count as a failure, otherwise it must return false.

  * :record-exception
    Configures a function which take a `throwable` as argument and
    evaluates if an exception should be recorded as a failure
    and thus increase the failure rate.
    The predicate function must return true if the exception should
    count as a failure. The predicate function must return false, if the exception
    should count as a success, unless the exception is explicitly ignored by
    :ignore-exceptions or :ignore-exception configurations.

  * :ignore-exception
    Configures a function which take a `throwable` as argument and
    evaluates if an exception should be ignored and neither count as a
    failure nor success.
    The predicate function must return true if the exception should be ignored .
    The predicate function must return false, if the exception should count as a failure.

  * :record-exceptions
    Configures a list of error classes that are recorded as a failure
    and thus increase the failure rate. Any exception matching or
    inheriting from one of the list should count as a failure, unless
    ignored via :ignore-exceptions. Ignoring an exception has priority
    over recording an exception.
    Example:
    {:record-exceptions [Throwable]
     :ignore-exceptions [RuntimeException]}
    would capture all Errors and checked Exceptions, and ignore
    unchecked exceptions.
    For a more sophisticated exception management use the
    :record-failure option.

  * :ignore-exceptions
    Configures a list of error classes that are ignored as a failure
     and thus do not increase the failure rate. Any exception matching
     or inheriting from one of the list will not count as a failure,
     even if marked via :record-exceptions. Ignoring an exception has
     priority over recording an exception.
     Example:
     {:ignore-exceptions [Throwable]
      :record-exceptions [Exception]}
     would capture nothing.
     Example:
     {:ignore-exceptions [Exception]
      :record-exceptions [Throwable]}
     would capture Errors.
     For a more sophisticated exception management use the
     :record-failure option.

  * :automatic-transfer-from-open-to-half-open?
    true to enable automatic transition from :OPEN to :HALF_OPEN state once
    the :wait-millis-in-open-state has passed.
   "
  [opts]
  (s/verify-opt-map-keys-with-spec :breaker/breaker-config opts)

  (if (empty? opts)
    (CircuitBreakerConfig/ofDefaults)
    (let [^CircuitBreakerConfig$Builder config (CircuitBreakerConfig/custom)]
      (when-let [failure-threshold (:failure-rate-threshold opts)]
        (.failureRateThreshold config (float failure-threshold)))
      (when-let [threshold (:slow-call-rate-threshold opts)]
        (.slowCallRateThreshold config (float threshold)))
      (when-let [stack-trace-enabled (:writable-stack-trace-enabled opts)]
        (.writableStackTraceEnabled config stack-trace-enabled))
      (when-let [duration (:wait-millis-in-open-state opts)]
        (.waitDurationInOpenState config (Duration/ofMillis duration)))
      (when-let [interval-fn (:wait-interval-function-in-open-state opts)]
        (.waitIntervalFunctionInOpenState config interval-fn))
      (when-let [duration (:slow-call-threshold-in-millis opts)]
        (.slowCallDurationThreshold config (Duration/ofMillis duration)))
      (when-let [calls (:permitted-number-of-calls-in-half-open-state opts)]
        (.permittedNumberOfCallsInHalfOpenState config calls))
      (when-let [size (:sliding-window-size opts)]
        (.slidingWindowSize config size))
      (when-let [calls (:minimum-number-of-calls opts)]
        (.minimumNumberOfCalls config calls))
      (when-let [type (:sliding-window-type opts)]
        (if (keyword? type)
          (.slidingWindowType config (u/keyword->enum CircuitBreakerConfig$SlidingWindowType type))
          (.slidingWindowType config type)))
      (when-let [record-exception (:record-exception opts)]
        (.recordException config (reify Predicate
                                   (test [_ v] (record-exception v)))))
      (when-let [ignore-exception (:ignore-exception opts)]
        (.ignoreException config (reify Predicate
                                   (test [_ v] (ignore-exception v)))))
      (when-let [exceptions (:record-exceptions opts)]
        (.recordExceptions config (into-array Class exceptions)))

      (when-let [exceptions (:ignore-exceptions opts)]
        (.ignoreExceptions config (into-array Class exceptions)))

      (when-let [enabled? (:automatic-transfer-from-open-to-half-open? opts)]
        (.automaticTransitionFromOpenToHalfOpenEnabled config enabled?))

      (.build config))))

(defn ^CircuitBreakerRegistry registry-with-config
  "Create a CircuitBreakerRegistry with a circuit breaker
   configurations map.

   Please refer to `circuit-breaker-config` for allowed key value pairs
   within the circuit breaker configuration map."
  [config]
  (let [^CircuitBreakerConfig c (if (instance? CircuitBreakerConfig config)
                                  config
                                  (circuit-breaker-config config))]
    (CircuitBreakerRegistry/of c)))

(defmacro defregistry
  "Define a CircuitBreakerRegistry under `name` with a default or custom
   circuit breaker configuration.

   Please refer to `circuit-breaker-config` for allowed key value pairs
   within the circuit breaker configuration map."
  ([name]
   (let [sym (with-meta (symbol name) {:tag `CircuitBreakerRegistry})]
     `(def ~sym (CircuitBreakerRegistry/ofDefaults))))
  ([name config]
   (let [sym (with-meta (symbol name) {:tag `CircuitBreakerRegistry})]
     `(def ~sym
        (let [config# (circuit-breaker-config ~config)]
          (registry-with-config config#))))))

(defn get-all-breakers
  "Get all circuit breakers registered to a CircuitBreakerRegistry"
  [^CircuitBreakerRegistry registry]
  (let [breakers (.getAllCircuitBreakers registry)
        iter (.iterator breakers)]
    (u/lazy-seq-from-iterator iter)))

(defn ^CircuitBreaker circuit-breaker
  "Create a circuit breaker with a `name` and a default or custom circuit breaker configuration.

   The `name` argument is only used to register this newly created circuit
   breaker to a CircuitBreakerRegistry. If you don't want to bind this circuit
   breaker with a CircuitBreakerRegistry, the `name` argument is ignored.

   Please refer to `circuit-breaker-config` for allowed key value pairs
   within the circuit breaker configurations map.

   If you want to register this circuit breaker to a CircuitBreakerRegistry,
   you need to put :registry key with a CircuitBreakerRegistry in the `config`
   argument. If you do not provide any other configurations, the newly created
   circuit breaker will inherit circuit breaker configurations from this
   provided CircuitBreakerRegistry
   Example:
   (circuit-breaker my-breaker {:registry my-registry})

   If you want to register this circuit breaker to a CircuitBreakerRegistry
   and you want to use new circuit breaker configurations to overwrite the configurations
   inherited from the registered CircuitBreakerRegistry,
   you need not only provide the :registry key with the CircuitBreakerRegistry in `config`
   argument but also provide other circuit breaker configurations you'd like to overwrite.
   Example:
   (circuit-breaker my-breaker {:registry my-registry
                                :failure-rate-threshold 50.0
                                :sliding-window-size 30
                                :permitted-number-of-calls-in-half-open-state 20})

   If you only want to create a circuit breaker and not register it to any
   CircuitBreakerRegistry, you just need to provide circuit breaker configurations in `config`
   argument. The `name` argument is ignored."
  ([^String name] (CircuitBreaker/ofDefaults name))
  ([^String name config]
   (let [^CircuitBreakerRegistry registry (:registry config)
         config (dissoc config :registry)]
     (cond
       (and registry (not-empty config))
       (let [config (circuit-breaker-config config)]
         (.circuitBreaker registry name ^CircuitBreakerConfig config))

       registry
       (.circuitBreaker registry name)

       :else
       (let [config (circuit-breaker-config config)]
         (CircuitBreaker/of name ^CircuitBreakerConfig config))))))

(defmacro defbreaker
  "Define a circuit breaker under `name` with a default or custom circuit breaker
   configuration.

   Please refer to `circuit-breaker-config` for allowed key value pairs
   within the circuit breaker configuration.

   If you want to register this circuit breaker to a CircuitBreakerRegistry,
   you need to put :registry key with a CircuitBreakerRegistry in the `config`
   argument. If you do not provide any other configurations, the newly created
   circuit breaker will inherit circuit breaker configurations from this
   provided CircuitBreakerRegistry
   Example:
   (defbreaker my-breaker {:registry my-registry})

   If you want to register this circuit breaker to a CircuitBreakerRegistry
   and you want to use new circuit breaker configurations to overwrite the configurations
   inherited from the registered CircuitBreakerRegistry,
   you need not only provide the :registry key with the CircuitBreakerRegistry in `config`
   argument but also provide other circuit breaker configurations you'd like to overwrite.
   Example:
   (defbreaker my-breaker {:registry my-registry
                           :failure-rate-threshold 50.0
                           :sliding-window-size 30
                           :permitted-number-of-calls-in-half-open-state 20})

   If you only want to create a circuit breaker and not register it to any
   CircuitBreakerRegistry, you just need to provide circuit breaker configuration in `config`
   argument without :registry keyword."
  ([name]
   (let [sym (with-meta (symbol name) {:tag `CircuitBreaker})
         ^String name-in-string (str *ns* "/" name)]
     `(def ~sym (circuit-breaker ~name-in-string))))
  ([name config]
   (let [sym (with-meta (symbol name) {:tag `CircuitBreaker})
         ^String name-in-string (str *ns* "/" name)]
     `(def ~sym (circuit-breaker ~name-in-string ~config)))))

(defn on-success
  "Records a successful call."
  [^CircuitBreaker breaker duration-in-nanos]
  (.onSuccess breaker duration-in-nanos TimeUnit/NANOSECONDS))

(defn on-error
  "Records a failed call.
   This method must be invoked when a call failed."
  [^CircuitBreaker breaker duration-in-nanos throwable]
  (.onError breaker duration-in-nanos TimeUnit/NANOSECONDS throwable))

(defn ^String name
  "Get the name of this CircuitBreaker"
  [^CircuitBreaker breaker]
  (.getName breaker))

(defn state
  "Get the state of the circuit breaker in keyword format.
   Currently, state can be one of :DISABLED, :CLOSED, :OPEN, :FORCED_OPEN, :HALF_OPEN"
  [^CircuitBreaker breaker]
  (u/enum->keyword (.getState breaker)))

(defn ^CircuitBreakerConfig config
  "Returns the configurations of this CircuitBreaker"
  [^CircuitBreaker breaker]
  (.getCircuitBreakerConfig breaker))

(defn reset!
  "Get the circuit breaker to its original closed state, losing statistics.
   Should only be used, when you want to want to fully reset the circuit breaker without creating a new one."
  [^CircuitBreaker breaker]
  (.reset breaker))

(defn transition-to-closed-state!
  "Transitions the circuit breaker state machine to CLOSED state.

   Should only be used, when you want to force a state transition.
   State transition are normally done internally.
  "
  [^CircuitBreaker breaker]
  (.transitionToClosedState breaker))

(defn transition-to-open-state!
  "Transitions the circuit breaker state machine to OPEN state.

   Should only be used, when you want to force a state transition.
   State transition are normally done internally."
  [^CircuitBreaker breaker]
  (.transitionToOpenState breaker))

(defn transition-to-half-open!
  "Transitions the circuit breaker state machine to HALF_OPEN state.

   Should only be used, when you want to force a state transition.
   State transition are normally done internally.
  "
  [^CircuitBreaker breaker]
  (.transitionToHalfOpenState breaker))

(defn transition-to-disabled-state!
  "Transitions the circut breaker state machine to a DISABLED state,
   stopping state transition, metrics and event publishing.

   Should only be used, when you want to disable the circuit breaker
   allowing all calls to pass. To recover from this state you must
   force a new state transition
  "
  [^CircuitBreaker breaker]
  (.transitionToDisabledState breaker))

(defn transition-to-forced-open-state!
  "Transitions the state machine to a FORCED_OPEN state,
   stopping state transition, metrics and event publishing.

   Should only be used, when you want to disable the circuit breaker
   allowing no call to pass. To recover from this state you must
   force a new state transition.
  "
  [^CircuitBreaker breaker]
  (.transitionToForcedOpenState breaker))

(defn metrics
  "Get the Metrics of this CircuitBreaker"
  [^CircuitBreaker breaker]
  (let [^CircuitBreaker$Metrics metric (.getMetrics breaker)]
    {:failure-rate (.getFailureRate metric)
     :slow-call-rate (.getSlowCallRate metric)
     :number-of-slow-calls (.getNumberOfSlowCalls metric)
     :number-of-slow-successful-calls (.getNumberOfSlowSuccessfulCalls metric)
     :number-of-slow-failed-calls (.getNumberOfSlowFailedCalls metric)
     :number-of-buffered-calls (.getNumberOfBufferedCalls metric)
     :number-of-failed-calls (.getNumberOfFailedCalls metric)
     :number-of-not-permitted-calls (.getNumberOfNotPermittedCalls metric)
     :number-of-successful-calls (.getNumberOfSuccessfulCalls metric)}))

(def ^{:dynamic true
       :doc     "Contextual value represents circuit breaker name"}
*breaker-name*)

(def ^{:dynamic true
       :doc "Contextual value represents event create time"}
*creation-time*)

(defmacro ^{:private true :no-doc true} with-context [abstract-event & body]
  (let [abstract-event (vary-meta abstract-event assoc :tag `CircuitBreakerEvent)]
    `(binding [*breaker-name* (.getCircuitBreakerName ~abstract-event)
               *creation-time* (.getCreationTime ~abstract-event)]
       ~@body)))

(defmulti ^:private on-event (fn [_ event] (.getEventType ^CircuitBreakerEvent event)))

(defmethod on-event CircuitBreakerEvent$Type/SUCCESS
  [consumer-fn-map ^CircuitBreakerOnSuccessEvent event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-success)]
      (consumer-fn (.toMillis (.getElapsedDuration event))))))

(defmethod on-event CircuitBreakerEvent$Type/ERROR
  [consumer-fn-map ^CircuitBreakerOnErrorEvent event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-error)]
      (consumer-fn (.getThrowable event) (.toMillis (.getElapsedDuration event))))))

(defmethod on-event CircuitBreakerEvent$Type/STATE_TRANSITION
  [consumer-fn-map ^CircuitBreakerOnStateTransitionEvent event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-state-transition)]
      (let [^CircuitBreaker$StateTransition state-trans (.getStateTransition event)]
        (consumer-fn (u/enum->keyword (.getFromState state-trans))
                     (u/enum->keyword (.getToState state-trans)))))))

(defmethod on-event CircuitBreakerEvent$Type/RESET
  [consumer-fn-map event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-reset)]
      (consumer-fn))))

(defmethod on-event CircuitBreakerEvent$Type/IGNORED_ERROR
  [consumer-fn-map ^CircuitBreakerOnIgnoredErrorEvent event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-ignored-error)]
      (consumer-fn (.getThrowable event) (.toMillis (.getElapsedDuration event))))))

(defmethod on-event CircuitBreakerEvent$Type/NOT_PERMITTED
  [consumer-fn-map event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-call-not-permitted)]
      (consumer-fn))))

(defmethod on-event CircuitBreakerEvent$Type/DISABLED
  [consumer-fn-map event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-disabled)]
      (consumer-fn))))

(defmethod on-event CircuitBreakerEvent$Type/FORCED_OPEN
  [consumer-fn-map event]
  (with-context event
    (when-let [consumer-fn (get consumer-fn-map :on-force-open)]
      (consumer-fn))))

(defn- create-consumer
  ([consumer-fn-map]
   (reify EventConsumer
     (consumeEvent [_ event]
       (on-event consumer-fn-map event))))
  ([k consumer-fn]
   (reify EventConsumer
     (consumeEvent [_ event]
       (on-event {k consumer-fn} event)))))

(defn set-on-success-event-consumer!
  [^CircuitBreaker breaker consumer-fn]
  "set a consumer to consume `on-success` event which emitted when request success from circuit breaker.
   `consumer-fn` accepts a function which takes `elapsed-millis` as arguments,
   which stands for the duration in milliseconds of the successful request.

   Please note that in `consumer-fn` you can get the circuit breaker name and the creation time of the
   consumed event by accessing `*breaker-name*` and `*creation-time*` under this namespace."
  (let [pub (.getEventPublisher breaker)]
    (.onSuccess pub (create-consumer :on-success consumer-fn))))

(defn set-on-error-event-consumer!
  [^CircuitBreaker breaker consumer-fn]
  "set a consumer to consume `on-error` event which emitted when request failed from circuit breaker.
   `consumer-fn` accepts a function which takes `throwable`, `elapsed-millis` as arguments.

   Please note that in `consumer-fn` you can get the circuit breaker name and the creation time of the
   consumed event by accessing `*breaker-name*` and `*creation-time*` under this namespace."
  (let [pub (.getEventPublisher breaker)]
    (.onError pub (create-consumer :on-error consumer-fn))))

(defn set-on-state-transition-event-consumer!
  [^CircuitBreaker breaker consumer-fn]
  "set a consumer to consume `on-state-transition` event which emitted when the state of the circuit breaker changed
   `consumer-fn` accepts a function which takes `from-state`, `to-state` as arguments.

   Please note that in `consumer-fn` you can get the circuit breaker name and the creation time of the
   consumed event by accessing `*breaker-name*` and `*creation-time*` under this namespace."
  (let [pub (.getEventPublisher breaker)]
    (.onStateTransition pub (create-consumer :on-state-transition consumer-fn))))

(defn set-on-reset-event-consumer!
  [^CircuitBreaker breaker consumer-fn]
  "set a consumer to consume `on-reset` event which emitted when the state of the circuit breaker reset to CLOSED
   `consumer-fn` accepts a function which takes no arguments.

   Please note that in `consumer-fn` you can get the circuit breaker name and the creation time of the
   consumed event by accessing `*breaker-name*` and `*creation-time*` under this namespace."
  (let [pub (.getEventPublisher breaker)]
    (.onReset pub (create-consumer :on-reset consumer-fn))))

(defn set-on-ignored-error-event!
  [^CircuitBreaker breaker consumer-fn]
  "set a consumer to consume `on-ignored-error` event which emitted when the request failed due to an error
   which we determine to ignore
   `consumer-fn` accepts a function which takes `throwable`, `elapsed-millis` as arguments.

   Please note that in `consumer-fn` you can get the circuit breaker name and the creation time of the
   consumed event by accessing `*breaker-name*` and `*creation-time*` under this namespace."
  (let [pub (.getEventPublisher breaker)]
    (.onIgnoredError pub (create-consumer :on-ignored-error consumer-fn))))

(defn set-on-call-not-permitted-consumer!
  [^CircuitBreaker breaker consumer-fn]
  "set a consumer to consume on call not permitted event which emitted when a request is
   refused due to circuit breaker open.
   `consumer-fn` accepts a function which takes no arguments.

   Please note that in `consumer-fn` you can get the circuit breaker name and the creation time of the
   consumed event by accessing `*breaker-name*` and `*creation-time*` under this namespace."
  (let [pub (.getEventPublisher breaker)]
    (.onCallNotPermitted pub (create-consumer :on-call-not-permitted consumer-fn))))

(defn set-on-all-event-consumer!
  [^CircuitBreaker breaker consumer-fn-map]
  "set a consumer to consume all available events emitted from circuit breaker.
   `consumer-fn-map` accepts a map which contains following key and function pairs:

   * `on-success` accepts a function which takes `elapsed-millis` as arguments
   * `on-error` accepts a function which takes `throwable`, `elapsed-millis` as arguments
   * `on-state-transition` accepts a function which takes `from-state`, `to-state` as arguments
   * `on-reset` accepts a function which takes no arguments
   * `on-ignored-error` accepts a function which takes `throwable`, `elapsed-millis` as arguments
   * `on-call-not-permitted` accepts a function which takes no arguments

   Please note that in `consumer-fn` you can get the circuit breaker name and the creation time of the
   consumed event by accessing `*breaker-name*` and `*creation-time*` under this namespace."
  (let [pub (.getEventPublisher breaker)]
    (.onEvent pub (create-consumer consumer-fn-map))))



