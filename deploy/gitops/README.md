# Argo CD GitOps boundary

`AppProject` restricts the repository and destination. `Application` tracks the
staging Kustomize overlay but intentionally has no automated sync until real
Harbor image references replace every sentinel value. Jenkins builds and
publishes; it does not receive Kubernetes credentials. Promotion changes the
six image references in Git through review, then Argo CD reconciles that commit.

Render before applying:

```powershell
kubectl kustomize deploy/gitops/environments/staging
kubectl kustomize deploy/gitops/argocd
```

Install Argo CD in a disposable cluster from its official release manifests,
then apply the rendered project and application only after the registry values
are immutable and reachable from that cluster.

`install-local-argocd.ps1 -Context <disposable-context>` installs the pinned
official Argo CD `v3.5.2` manifest. It refuses a context mismatch and names that
look production-like. The Application has no automated sync while sentinel
image tags remain, so installation cannot deploy placeholder images.
