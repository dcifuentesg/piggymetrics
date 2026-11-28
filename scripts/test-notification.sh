#!/usr/bin/env bash
# Verificacion de componente: notification-service, logica (RecipientServiceImpl, casos RSI-xx).
# Uso, desde la raiz del repo:  scripts/test-notification.sh      (requiere JDK 8 y Maven)
set -euo pipefail
cd "$(dirname "$0")/.."
MOD=notification-service
MVN="mvn -B -pl $MOD"
CLS=com.piggymetrics.notification.service.RecipientServiceImpl
PIT=org.pitest:pitest-maven:mutationCoverage

echo "== 1/3 Suite de LOGICA: RecipientServiceImpl + JaCoCo =="
$MVN clean
$MVN -P component-tests test -Dtest=RecipientServiceImplComponentTest -DfailIfNoTests=false
cat $MOD/target/component-verdicts.txt

echo "== 2/3 Cobertura de ramas (JaCoCo) =="
head -1 $MOD/target/site/jacoco/jacoco.csv; grep ',RecipientServiceImpl,' $MOD/target/site/jacoco/jacoco.csv

echo "== 3/3 Mutacion (PIT) =="
for spec in "RecipientServiceImplTest DEFAULTS rsi-original" "RecipientServiceImplComponentTest DEFAULTS rsi" \
            "RecipientServiceImplTest ALL rsi-original-all" "RecipientServiceImplComponentTest ALL rsi-all"; do
  set -- $spec
  $MVN -P component-tests $PIT -DtargetClasses=$CLS -DtargetTests=com.piggymetrics.notification.service.$1 \
    -Dmutators=$2 -DreportsDirectory=target/pit-reports/$3
done
echo "Reportes: $MOD/target/component-verdicts.txt, $MOD/target/site/jacoco/index.html, $MOD/target/pit-reports/"
