# Milestone 12 CI/CD demosu

## Amaç

Credential expose etmeden ve pahalı stage'leri gereksiz yere yeniden çalıştırmadan vacancy-aligned delivery chain'i göstermek.

## Senaryo

1. `Jenkinsfile` dosyasını gösterin: Java/React quality, SonarQube gate, Nexus, SBOM ve Harbor publication dependency order içinde çalışır.
2. Jenkins Build #10'u gösterin: complete pipeline source revision `6c07fa81df22330699c58574059b89e58777f0ed` üzerinde başarıyla tamamlandı.
3. Nexus'u sorgulayın ve `maven-snapshots` altında altı Maven component gösterin. Anonymous metadata access'in `403` döndürdüğünü, `jenkins-publisher` hesabının ise repository-scoped role üzerinden browse/download yapabildiğini gösterin.
4. Harbor `health-insurance` project'ini açın ve immutable tag `6c07fa81df22330699c58574059b89e58777f0ed` taşıyan altı repository'yi gösterin. Project'in private olduğunu ve 90 günlük robot'un yalnızca push/pull/read/create access'e sahip olduğunu gösterin.
5. `deploy/gitops/environments/staging` render edin ve aynı tag'i gösterin.
6. Argo CD Application `health-insurance-staging` ekranını gösterin: `Synced`, operation `Succeeded`, desired-state revision `c9c1baa496df1c0126648573c5f25a75f6967a5c`.
7. AppProject'in explicit resource allowlist'e sahip olduğunu ve Secret veya namespace RBAC yönetemediğini gösterin. Ardından `harbor-registry` kullanan SHA-tagged pod'ları gösterin ve runtime image ID'lerini Harbor manifest digest'leriyle karşılaştırın.
8. Nexus içindeki bir `build-provenance.json` attachment'ını açın, OCI `revision` label'ını inceleyin ve her ikisini Kustomize source-revision annotation ile deployed image tag'e karşılaştırın.

## Mülakat açıklaması

“Pipeline publication öncesinde fail-closed davranır. Maven artifact'ları Nexus'a, OCI image'ları Harbor'a gider. Image'lar hiçbir zaman `latest` ile promote edilmez; full Git SHA Kustomize'a commit edilir. Argo CD desired state'i Git'ten pull eder; böylece deployment reproducible ve auditable olur. Publication retry, zaten başarılı olmuş test suite'i tekrar çalıştırmaz.”

## Güvenli sıfırlama

Disposable cluster'ı `minikube stop -p portfolio-ci` ile durdurun. Nexus, Harbor, Jenkins ve SonarQube data local Docker volume'larda yaşar. `infra/cicd/.env`, generated Harbor runtime file'ları, token veya password commit etmeyin. Automatic Trivy scanning bu portfolio flow için bilinçli olarak kapalıdır ve tamamlanmış mandatory gate olarak sunulmamalıdır.
