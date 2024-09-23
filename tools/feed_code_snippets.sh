#!/bin/bash
set -x

vespa feed -t http://localhost:8080 --connections 5 data/code_snippet_index.json
