import argparse
import requests
from typing import List
from ir_measures import calc_aggregate, nDCG, ScoredDoc, Qrel, Success, Recall, Judged, Precision

def parse_vespa_response(response: dict, qid: str) -> List[ScoredDoc]:
    result = []
    hits = response['root'].get('children', [])
    for hit in hits:
        doc_id = hit['fields']['path']
        relevance = hit['relevance']
        result.append(ScoredDoc(qid, doc_id, relevance))
    return result

def search(query: str, qid: str, ranking: str, endpoint: str, hits=12) -> List[ScoredDoc]:
    """Search Vespa for a given query and ranking profile. Filters by namespace 'open-p'."""
    query_request = {
        'query': query,
        'ranking.profile': ranking,
        'queryProfile': 'llmsearch',
        'hits': hits,
        'filters': '+namespace:open-p'
    }
    response = requests.post(f"{endpoint}/search/", json=query_request)
    if response.ok:
        return parse_vespa_response(response.json(), qid)
    else:
        print("Search request failed with response " + str(response.json()))
        return []

def main():
    parser = argparse.ArgumentParser(description="Evaluate rank profiles for Vespa RAG backend.")
    parser.add_argument("qrels_file", type=str, help="Path to the qrels file (https://github.com/vespa-cloud/vespa-documentation-search/blob/main/eval/qrels.tsv)")
    parser.add_argument("rank_profiles", type=str, help="Comma-separated list of rank profiles to test")
    parser.add_argument("--endpoint", type=str, default="http://localhost:8080", help="Search API endpoint")

    args = parser.parse_args()
    
    qrels_file = args.qrels_file
    rank_profiles = args.rank_profiles.split(',')
    endpoint = args.endpoint

    qrels = []
    queries = {}
    with open(qrels_file) as f:
        for line in f:
            qid, query, doc_id, rel = line.strip().split("\t")
            rel = Qrel(qid, doc_id, int(rel))
            qrels.append(rel)
            queries[query] = qid

    results = {profile: [] for profile in rank_profiles}
    metrics = [nDCG@12, Recall(cutoff=2)@12, Judged@12]

    for query_text, qid in queries.items():
        for profile in rank_profiles:
            results[profile].extend(search(query_text, qid, profile, endpoint))

    aggregates = {profile: calc_aggregate(metrics, qrels, results[profile]) for profile in rank_profiles}
    sorted_profiles = sorted(rank_profiles, key=lambda profile: aggregates[profile][metrics[0]], reverse=True)

    for profile in sorted_profiles:
        aggregate = aggregates[profile]
        metrics_output = ', '.join([f"{metric}: {aggregate[metric]:.4f}" for metric in metrics])
        print(f"{profile}: {metrics_output}")
    import matplotlib.pyplot as plt
    profile_names = [profile for profile in sorted_profiles]
    first_metric_values = [aggregates[profile][metrics[0]] for profile in sorted_profiles]


    metric = metrics[0]
    max_value = max(first_metric_values)
    colors = ['skyblue' if value != max_value else 'orange' for value in first_metric_values]
    plt.figure(figsize=(10, 5))
    bars = plt.bar(profile_names, first_metric_values, color=colors)
    # Add value labels to each bar
    for bar in bars:
      yval = bar.get_height()
      plt.text(bar.get_x() + bar.get_width()/2, yval, f'{yval:.4f}', ha='center', va='bottom')

    plt.xlabel('rank-profile')
    plt.ylabel(metric)
    plt.title(f'{metric} for different rank-profiles', fontweight="bold")
    plt.xticks(rotation=45)
    plt.tight_layout()
    print("Saving plot to ranking_metrics.png")
    plt.savefig('ranking_metrics.png')

if __name__ == "__main__":
    main()
