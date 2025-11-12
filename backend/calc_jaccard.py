import json
import pathlib

def tokenize(text):
    text = text or ''
    text = text.strip()
    if not text:
        return []
    return text.split()

def jaccard(tokens_a, tokens_b):
    set_a, set_b = set(tokens_a), set(tokens_b)
    if not set_a and not set_b:
        return 1.0
    union = set_a | set_b
    if not union:
        return 1.0
    return len(set_a & set_b) / len(union)

base = pathlib.Path('backend/target/api-rerun-20251103-220207')
files = {
    'settlement': '_api_scan_detail_src_main_java_com_example_migration_billing_SettlementProcessor.java.json',
    'customer': '_api_scan_detail_src_main_java_com_example_migration_customer_CustomerSyncService.java.json'
}
for key, name in files.items():
    data = json.loads((base / name).read_text(encoding='utf-8-sig'))['data']
    oracle_tokens = tokenize(data['oracleDiff']['after'])
    gauss_tokens = tokenize(data['gaussDiff']['before'])
    print(key, 'oracle_tokens', len(oracle_tokens), 'gauss_tokens', len(gauss_tokens), 'jaccard', round(jaccard(oracle_tokens, gauss_tokens), 4))
