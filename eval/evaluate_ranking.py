from calendar import c
import ir_measures
from numpy import c_
import requests
from ir_measures import calc_aggregate, nDCG, ScoredDoc, Success
import ir_measures
from enum import Enum
from typing import List



def parse_vespa_response(response:dict, qid:str) -> List[ScoredDoc]:
    result = []
    hits = response['root'].get('children',[])
    for hit in hits:
      doc_id = hit['fields']['path']
      relevance = hit['relevance']
      result.append(ScoredDoc(qid, doc_id, relevance))
    return result

def search(query:str, qid:str, ranking:str, 
           hits=10) -> List[ScoredDoc]:
    
    query_request = {
        'query': query,
        'ranking.profile': ranking,
        'queryProfile': 'llmsearch',
        'hits' : hits, 
        'filters': '+namespace:open-p'
    }
    response = requests.post("http://localhost:8080/search/", json=query_request)
    if response.ok:
        return parse_vespa_response(response.json(), qid)
    else:
      print("Search request failed with response " + str(response.json()))
      return []

def main():
  with open("qrels.tsv") as f:
    qrels = []
    queries = {}
    with open("qrels.tsv") as f:
      for line in f:
        qid, query, doc_id, rel = line.strip().split("\t")
        rel = ir_measures.Qrel(qid, doc_id, int(rel))
        qrels.append(rel)
        queries[query] = qid

  a_results = []
  b_results = []
  c_results = []
  d_results = []
  e_results = []
  metrics = [nDCG@10]
  for query_text, qid in queries.items():
    a_results.extend(search(query_text, qid, "hybrid"))
    b_results.extend(search(query_text, qid, "hybrid2"))
    c_results.extend(search(query_text, qid, "hybrid3"))
    d_results.extend(search(query_text, qid, "bm25"))
    e_results.extend(search(query_text, qid, "semantic"))

  a = calc_aggregate(metrics, qrels, a_results)
  b = calc_aggregate(metrics, qrels, b_results)
  c = calc_aggregate(metrics, qrels, c_results)
  d = calc_aggregate(metrics, qrels, d_results)
  e = calc_aggregate(metrics, qrels, e_results)
  print("Hybrid {}".format(a))
  print("Hybrid2 {}".format(b))
  print("Hybrid3 {}".format(c))
  print("BM25 {}".format(d))
  print("Semantic {}".format(e))  

if __name__ == "__main__":
    main()