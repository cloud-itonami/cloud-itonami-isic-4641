(ns textiletrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [textiletrade.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest all-four-seeded-jurisdictions-have-required-evidence
  ;; every seeded textile-wholesale jurisdiction actually has a real
  ;; required-evidence set reported honestly here
  (doseq [iso3 ["JPN" "USA" "GBR" "DEU"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

;; ----------------------------- forced-labor rebuttable presumption -----------------------------

(deftest xinjiang-is-a-flagged-origin
  (is (facts/flagged-origin? "Xinjiang Uyghur Autonomous Region, China")))

(deftest north-korea-linked-origin-is-flagged
  (is (facts/flagged-origin? "Democratic People's Republic of Korea (North Korea) -- labor-linked production")))

(deftest an-ordinary-manufacturing-origin-is-not-flagged
  (is (not (facts/flagged-origin? "Vietnam -- Ho Chi Minh City garment district"))))

(deftest usa-has-a-currently-binding-forced-labor-import-ban-statute
  (is (facts/forced-labor-import-ban-binding? "USA")))

(deftest deu-forced-labor-statute-is-seeded-but-not-yet-binding
  ;; Regulation (EU) 2024/3015 is adopted but phased application has not
  ;; yet taken effect -- seeded for forward-compatibility, not a trigger.
  (is (some? (facts/forced-labor-presumption-citation "DEU")) "citation exists for forward-compatibility")
  (is (not (facts/forced-labor-import-ban-binding? "DEU")) "not yet binding as of this catalog"))

(deftest jpn-and-gbr-have-no-forced-labor-import-ban-statute-seeded
  ;; honest gap: no fabricated JPN/GBR equivalent to UFLPA/CAATSA
  (is (nil? (facts/forced-labor-presumption-citation "JPN")))
  (is (nil? (facts/forced-labor-presumption-citation "GBR")))
  (is (not (facts/forced-labor-import-ban-binding? "JPN")))
  (is (not (facts/forced-labor-import-ban-binding? "GBR"))))

(deftest presumption-triggered-needs-both-binding-jurisdiction-and-flagged-origin-or-entity
  (is (facts/forced-labor-presumption-triggered?
       {:jurisdiction "USA" :origin-region "Xinjiang Uyghur Autonomous Region, China"})
      "flagged origin + binding jurisdiction -> triggered")
  (is (facts/forced-labor-presumption-triggered?
       {:jurisdiction "USA" :origin-region "Vietnam -- Ho Chi Minh City garment district"
        :entity-list-flagged? true})
      "flagged entity + binding jurisdiction -> triggered even with a clean origin-region")
  (is (not (facts/forced-labor-presumption-triggered?
            {:jurisdiction "JPN" :origin-region "Xinjiang Uyghur Autonomous Region, China"}))
      "flagged origin but NON-binding jurisdiction -> NOT triggered (jurisdiction-gated)")
  (is (not (facts/forced-labor-presumption-triggered?
            {:jurisdiction "USA" :origin-region "Vietnam -- Ho Chi Minh City garment district"}))
      "binding jurisdiction but clean origin/entity -> NOT triggered"))
