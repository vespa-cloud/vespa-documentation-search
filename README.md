<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://vespa.ai/assets/vespa-ai-logo-heather.svg">
  <source media="(prefers-color-scheme: light)" srcset="https://vespa.ai/assets/vespa-ai-logo-rock.svg">
  <img alt="#Vespa" width="200" src="https://vespa.ai/assets/vespa-ai-logo-rock.svg" style="margin-bottom: 25px;">
</picture>

# Vespa Documentation Search
Vespa Documentation Search is a Vespa Cloud instance for searching documents in:
* [vespa.ai](https://vespa.ai/)
  [![Vespa.ai Search Feed](https://github.com/vespa-engine/frontpage/actions/workflows/feed.yml/badge.svg)](https://github.com/vespa-engine/frontpage/actions/workflows/feed.yml)
* [docs.vespa.ai](https://docs.vespa.ai/)
  [![Vespa Documentation Search Feed](https://github.com/vespa-engine/documentation/actions/workflows/feed.yml/badge.svg)](https://github.com/vespa-engine/documentation/actions/workflows/feed.yml)
* [cloud.vespa.ai](https://cloud.vespa.ai/)
  [![Vespa Cloud Documentation Search Feed](https://github.com/vespa-engine/cloud/actions/workflows/feed.yml/badge.svg)](https://github.com/vespa-engine/cloud/actions/workflows/feed.yml)
* [blog.vespa.ai](https://blog.vespa.ai/)
  [![Vespa Blog Search Feed](https://github.com/vespa-engine/blog/actions/workflows/feed.yml/badge.svg)](https://github.com/vespa-engine/blog/actions/workflows/feed.yml)
* [Vespa Sample applications](https://github.com/vespa-engine/sample-apps)
  [![Vespa Sample-Apps Search Feed](https://github.com/vespa-engine/sample-apps/actions/workflows/feed.yml/badge.svg)](https://github.com/vespa-engine/sample-apps/actions/workflows/feed.yml)
* [pyvespa](https://pyvespa.readthedocs.io/en/latest/index.html)
  [![pyvespa Search Feed](https://github.com/vespa-engine/pyvespa/actions/workflows/feed.yml/badge.svg)](https://github.com/vespa-engine/pyvespa/actions/workflows/feed.yml)

This sample app is auto-deployed to Vespa Cloud,
see [deploy-vespa-documentation-search.yaml](https://github.com/vespa-cloud/vespa-documentation-search/actions/workflows/deploy-vespa-documentation-search.yaml)

![Vespa-Documentation-Search-Architecture](img/Vespa-Documentation-Search-Architecture.svg)

Deployment status:
* [![Deploy vespa-documentation-search to Vespa Cloud](https://github.com/vespa-cloud/vespa-documentation-search/actions/workflows/deploy-vespa-documentation-search.yaml/badge.svg)](https://github.com/vespa-cloud/vespa-documentation-search/actions/workflows/deploy-vespa-documentation-search.yaml)
* [![Vespa Cloud Documentation Search Deployment](https://api.vespa-external.aws.oath.cloud/badge/v1/vespa-team/vespacloud-docsearch/default/)](https://console.vespa.oath.cloud/tenant/vespa-team/application/vespacloud-docsearch/prod/deployment)



## Query API
<!-- ToDo: consider new names in endpoints -->
Open API endpoints:
* https://doc-search.vespa.oath.cloud/document/v1/
* https://doc-search.vespa.oath.cloud/search/

Example requests:
* https://doc-search.vespa.oath.cloud/document/v1/open/doc/docid/open%2Fen%2Freference%2Fquery-api-reference.html
* https://doc-search.vespa.oath.cloud/search/?yql=select+*+from+doc+where+userInput(@userinput)%3B&userinput=vespa+ranking+is+great

<pre data-test="exec" data-test-assert-contains="namespace">
$ curl "https://doc-search.vespa.oath.cloud/document/v1/open/doc/docid/open%2Fen%2Freference%2Fquery-api-reference.html"
</pre>
<pre data-test="exec" data-test-assert-contains="the-great-search-engine-debate">
$ curl --data-urlencode 'yql=select * from doc where userInput(@userinput)' \
  --data-urlencode 'userinput=vespa ranking is great' \
  https://doc-search.vespa.oath.cloud/search/
</pre>

Using these endpoints is a good way to get started with Vespa -
see the [github deploy action](.github/workflows/deploy-vespa-documentation-search.yaml)
(use `vespa:deploy` to deploy to a dev instance or the [quick-start](https://docs.vespa.ai/en/vespa-quick-start.html))
to deploy using Docker.

Refer to [getting-started-ranking](https://docs.vespa.ai/en/getting-started-ranking.html)
for example use of the Query API.


### Feed your own instance
It is easy to set up your own instance on Vespa Cloud and feed documents from
[vespa-engine/documentation](https://github.com/vespa-engine/documentation/):

1: Generate the `open_index.json` feed file:
  `cd vespa-engine/documentation && bundle exec jekyll build -p _plugins-vespafeed`.
  Refer to the [vespa_index_generator.rb](https://github.com/vespa-engine/documentation/blob/master/_plugins-vespafeed/vespa_index_generator.rb)
  for how the feed file is generated.

2: Add data plane credentials:

    $ pwd; ll *.pem
    /Users/myuser/github/vespa-engine/documentation
    -rwxr-xr-x@ 1 myuser  staff  3272 Mar 17 09:30 data-plane-private-key.pem
    -rwxr-xr-x@ 1 myuser  staff  1696 Mar 17 09:30 data-plane-public-key.pem

3: Set endpoint in `_config.yml` (get this from the Vespa Cloud Console):
```
diff --git a/_config.yml b/_config.yml
...
     feed_endpoints:
-        - url: https://vespacloud-docsearch.vespa-team.aws-us-east-1c.z.vespa-app.cloud/
-          indexes:
-              - open_index.json
-        - url: https://vespacloud-docsearch.vespa-team.aws-ap-northeast-1a.z.vespa-app.cloud/
+        - url: https://myinstance.vespacloud-docsearch.mytenant.aws-us-east-1c.dev.z.vespa-app.cloud/
           indexes:
```

Feed `open_index.json`:

    $ ./feed_to_vespa.py



## Ranking
The [ranking](src/main/application/schemas/doc.sd) is quite simplistic,
and an introduction to using _query rank features_ and _summary features_:
```
    rank-profile documentation inherits default {
        inputs {
            query(titleWeight) double: 2.0
            query(headersWeight) double: 1.0
            query(contentWeight) double: 1.0
            query(keywordsWeight) double: 10.0
            query(pathWeight) double: 1.0
        }
        first-phase {
            expression {
                query(titleWeight) * bm25(title) +
                query(contentWeight) * bm25(content) +
                query(headersWeight) * bm25(headers) +
                query(pathWeight) * bm25(path) +
                query(keywordsWeight) * bm25(keywords)
            }
        }
        summary-features {
            query(titleWeight)
            query(contentWeight)
            query(headersWeight)
            query(pathWeight)
            fieldMatch(title)
            fieldMatch(content)
            fieldMatch(content).matches
            fieldLength(title)
            fieldLength(content)
            bm25(title)
            bm25(content)
            bm25(headers)
            bm25(path)
            bm25(keywords)
        }
    }
```
With this it is easy to experiment with ranking by sending rank-properties in the query
and observing the values in summary-features, like:

[doc-search.vespa.oath.cloud/search/?yql=select * from doc where userInput(@userinput)&ranking=documentation&input.query(pathWeight)=10&userinput=vespa ranking is great](https://doc-search.vespa.oath.cloud/search/?yql=select%20*%20from%20doc%20where%20userInput(@userinput)&ranking=documentation&input.query(pathWeight)=10&userinput=vespa%20ranking%20is%20great)

See [approximate-nn-hnsw.md](https://raw.githubusercontent.com/vespa-engine/documentation/master/en/approximate-nn-hnsw.md)
for use of (comma separated) keywords set in the frontmatter to rank higher for those, e.g.

    ---
    title: "Approximate Nearest Neighbor Search using HNSW Index"
    keywords: "ann, approximate nearest neighbor"
    ---



## Document feed automation
Vespa Documentation is stored in GitHub:
* https://github.com/vespa-engine/documentation and https://github.com/vespa-engine/frontpage
* https://github.com/vespa-engine/cloud
* https://github.com/vespa-engine/blog
* https://github.com/vespa-engine/sample-apps
* https://pyvespa.readthedocs.io/en/latest/index.html

Jekyll is used to serve the documentation, it rebuilds at each commit.

A change also triggers GitHub Actions.
The _Build_ step in the workflow uses the Jekyll Generator plugin to build a JSON feed, used in the _Feed_ step:
* https://github.com/vespa-engine/documentation/blob/master/.github/workflows/feed.yml
* https://github.com/vespa-engine/documentation/blob/master/_config.yml
* https://github.com/vespa-engine/documentation/blob/master/_plugins/vespa_index_generator.rb

Vespa Cloud secures endpoints using mTLS. Secrets can be stored in GitHub Settings for a repository.
Here, the private key secret is accessed in the GitHub Actions workflow that feeds to Vespa Cloud:
[feed.yml](https://github.com/vespa-engine/documentation/blob/master/.github/workflows/feed.yml)



## Document processing
The documents are split into paragraphs for multi-vector ranking, see example in
[feed-split.py](https://github.com/vespa-engine/documentation/blob/master/feed-split.py).
<!-- ToDo: link to blogpost when published -->



## Query integration
Query results are open to the internet. To access Vespa Documentation Search,
an AWS Lambda function is used to get the private key secret from AWS Parameter Store,
then add it to the https request to Vespa Cloud:

The lambda needs AmazonSSMReadOnlyAccess added to its Role to access the Parameter Store.

Note JSON-P being used (_jsoncallback=_) - this simplifies the search result page:
[search.html](https://github.com/vespa-engine/documentation/blob/master/search.html).

<!-- ToDo: ref to Vespa JSON interface for this quirk -->



## Vespa Cloud Development and Deployments
This is a Vespa Cloud application and has hence implemented
[automated deployments](https://cloud.vespa.ai/en/automated-deployments).

The feed can contain an array of links from each document.
The [OutLinksDocumentProcessor](src/main/java/ai/vespa/cloud/docsearch/OutLinksDocumentProcessor.java)
is custom java code that add an in-link in each target document using the Vespa Document API.

To test this functionality, the
[VespaDocSystemTest](src/test/java/ai/vespa/cloud/docsearch/VespaDocSystemTest.java) runs for each deployment.

Creating a System Test is also a great way to develop a Vespa application:
* Use this application as a starting point
* Create a Vespa Cloud tenant (i.e. account), and set _tenant_ in [pom.xml](pom.xml)
* Deploy the application to Vespa Cloud
* Run the System Test from maven or IDE using the
  [Endpoint](https://github.com/vespa-engine/vespa/blob/master/tenant-cd-api/src/main/java/ai/vespa/hosted/cd/Endpoint.java)

<!-- ToDo: link to a Vespa Cloud Developer Guide once completed -->


## Feed grouping examples
```
cat << EOF | vespa feed -t https://vespacloud-docsearch.vespa-team.aws-us-east-1c.z.vespa-app.cloud -
{"fields": {"customer": "Smith","date": 1157526000,"item": "Intake valve","price": "1000","tax": "0.24"},"put": "id:purchase:purchase::0"}
{"fields": {"customer": "Smith","date": 1157616000,"item": "Rocker arm","price": "1000","tax": "0.12"},"put": "id:purchase:purchase::1"}
{"fields": {"customer": "Smith","date": 1157619600,"item": "Spring","price": "2000","tax": "0.24"},"put": "id:purchase:purchase::2"}
{"fields": {"customer": "Jones","date": 1157709600,"item": "Valve cover","price": "3000","tax": "0.12"},"put": "id:purchase:purchase::3"}
{"fields": {"customer": "Jones","date": 1157702400,"item": "Intake port","price": "5000","tax": "0.24"},"put": "id:purchase:purchase::4"}
{"fields": {"customer": "Brown","date": 1157706000,"item": "Head","price": "8000","tax": "0.12"},"put": "id:purchase:purchase::5"}
{"fields": {"customer": "Smith","date": 1157796000,"item": "Coolant","price": "1300","tax": "0.24"},"put": "id:purchase:purchase::6"}
{"fields": {"customer": "Jones","date": 1157788800,"item": "Engine block","price": "2100","tax": "0.12"},"put": "id:purchase:purchase::7"}
{"fields": {"customer": "Brown","date": 1157792400,"item": "Oil pan","price": "3400","tax": "0.24"},"put": "id:purchase:purchase::8"}
{"fields": {"customer": "Smith","date": 1157796000,"item": "Oil sump","price": "5500","tax": "0.12"},"put": "id:purchase:purchase::9"}
{"fields": {"customer": "Jones","date": 1157875200,"item": "Camshaft","price": "8900","tax": "0.24"},"put": "id:purchase:purchase::10"}
{"fields": {"customer": "Brown","date": 1157878800,"item": "Exhaust valve","price": "1440","tax": "0.12"},"put": "id:purchase:purchase::11"}
{"fields": {"customer": "Brown","date": 1157882400,"item": "Rocker arm","price": "2330","tax": "0.24"},"put": "id:purchase:purchase::12"}
{"fields": {"customer": "Brown","date": 1157875200,"item": "Spring","price": "3770","tax": "0.12"},"put": "id:purchase:purchase::13"}
{"fields": {"customer": "Smith","date": 1157878800,"item": "Spark plug","price": "6100","tax": "0.24"},"put": "id:purchase:purchase::14"}
{"fields": {"customer": "Jones","date": 1157968800,"item": "Exhaust port","price": "9870","tax": "0.12"},"put": "id:purchase:purchase::15"}
{"fields": {"customer": "Brown","date": 1157961600,"item": "Piston","price": "1597","tax": "0.24"},"put": "id:purchase:purchase::16"}
{"fields": {"customer": "Smith","date": 1157965200,"item": "Connection rod","price": "2584","tax": "0.12"},"put": "id:purchase:purchase::17"}
{"fields": {"customer": "Jones","date": 1157968800,"item": "Rod bearing","price": "4181","tax": "0.24"},"put": "id:purchase:purchase::18"}
{"fields": {"customer": "Jones","date": 1157972400,"item": "Crankshaft","price": "6765","tax": "0.12"},"put": "id:purchase:purchase::19"}
EOF
```


## Simplified node.js Lambda code
<pre>
'use strict';
const https = require('https')
const AWS = require('aws-sdk')

const publicCert = `-----BEGIN CERTIFICATE-----
MIIFbDCCA1QCCQCTyf46/BIdpDANBgkqhkiG9w0BAQsFADB4MQswCQYDVQQGEwJO
...
NxoOxvYcP8Pnxn8UGILy7sKl3VRQWIMrlOfXK4DEg8EGqeQzlFVScfSdbH0i6gQz
-----END CERTIFICATE-----`;

exports.handler = async (event, context) => {
    console.log('Received event:', JSON.stringify(event, null, 4));
    const query = event.queryStringParameters.query ? event.queryStringParameters.query : '';
    const jsoncallback = event.queryStringParameters.jsoncallback;
    const path = encodeURI(`/search/?jsoncallback=${jsoncallback}&query=${query}&hits=${hits}&ranking=${ranking}`);

    const ssm = new AWS.SSM();
    const privateKeyParam = await new Promise((resolve, reject) => {
        ssm.getParameter({
            Name: 'ThePrivateKey',
            WithDecryption: true
        }, (err, data) => {
            if (err) { return reject(err); }
            return resolve(data);
        });
    });

    var options = {
        hostname: 'vespacloud-docsearch.vespa-team.aws-us-east-1c.z.vespa-app.cloud',
        port: 443,
        path: path,
        method: 'GET',
        headers: { 'accept': 'application/json' },
        key: privateKeyParam.Parameter.Value,
        cert: publicCert
    }

    var body = '';
    const response = await new Promise((resolve, reject) => {
        const req = https.get(
            options,
            res => {
                res.setEncoding('utf8');
                res.on('data', (chunk) => {body += chunk})
                res.on('end', () => {
                    resolve({
                        statusCode: 200,
                        body: body
                    });
                });
            });
        req.on('error', (e) => {
          reject({
              statusCode: 500,
              body: 'Something went wrong!'
          });
        });
    });
    return response
};
</pre>

<!-- This repo moved from https://github.com/vespa-engine/sample-apps/blob/701725b88e5f6e0f50e25a62495ae7f5ebf1f9a7/vespa-cloud/vespa-documentation-search/README.md -->

<!-- dummy change -->
