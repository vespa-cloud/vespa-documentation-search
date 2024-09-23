#!/bin/bash
set -x

vespa feed -t http://localhost:8080 --connections 5 data/paragraph_index.json
