<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

![Vespa Cloud logo](https://cloud.vespa.ai/assets/logos/vespa-cloud-logo-full-black.png)

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



### Security
Vespa Cloud secures endpoints using mTLS. Secrets can be stored in GitHub Settings for a repository.
Here, the private key secret is accessed in the GitHub Actions workflow that feeds to Vespa Cloud:
[feed.yml](https://github.com/vespa-engine/documentation/blob/master/.github/workflows/feed.yml)



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



## Generate and feed search suggestions
Use the script in [search-suggestions](https://github.com/vespa-engine/sample-apps/tree/master/incremental-search/search-suggestions/)
to generate suggestions (generate ../../../documentation/open_index.json first):

    $ python3 ../../incremental-search/search-suggestions/count_terms.py \
      ../../../documentation/open_index.json feed_terms.json 2 ../../incremental-search/search-suggestions/top100en.txt
    $ curl -L -o vespa-feed-client-cli.zip \
      https://search.maven.org/remotecontent?filepath=com/yahoo/vespa/vespa-feed-client-cli/7.527.20/vespa-feed-client-cli-7.527.20-zip.zip
    $ unzip vespa-feed-client-cli.zip
    $ ./vespa-feed-client-cli/vespa-feed-client \
      --file feed_terms.json \
      --certificate ../../../documentation/data-plane-public-key.pem --privateKey ../../../documentation/data-plane-private-key.pem \
      --endpoint https://vespacloud-docsearch.vespa-team.aws-us-east-1c.z.vespa-app.cloud/

The above feeds single terms and phrases of 2, with stop-word removal from top100en.txt.
Suggestions with 3 terms creates a lot of noise -
work around by adding to this file and feed it:

    $ ./vespa-feed-client-cli/vespa-feed-client \
      --file extra_suggestions.json \
      --certificate ../../../documentation/data-plane-public-key.pem --privateKey ../../../documentation/data-plane-private-key.pem \
      --endpoint https://vespacloud-docsearch.vespa-team.aws-us-east-1c.z.vespa-app.cloud/



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
