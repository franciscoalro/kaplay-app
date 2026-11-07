# SDD ledger — plan: user-provided KA PLAY Home categories correction
Pre-flight: Home category rendering, provider aggregation, and reward placement share the Home presentation/data path; pagination details are isolated behind existing CatalogTarget loaders.
Ruling: preserve the existing 18-item Home preview as the visual cap, but remove any upstream truncation before aggregation; this keeps performance while allowing dynamic sections and accurate `hasMore`/Ver tudo behavior.
