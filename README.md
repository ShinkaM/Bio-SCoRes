# Bio-SCoRes
A smorgasbord architecture for coreference resolution in biomedical text

Bio-SCoRes is a general, modular framework for coreference resolution in 
biomedical text. It is underpinned by a smorgasbord architecture, and 
incorporates a variety of coreference types (anaphora, appositive, etc.), 
their textual expressions (definite noun phrases, possessive pronouns, 
etc.) and allows fine-grained specification of coreference resolution 
strategies. 

This Bio-SCoRes distribution includes the coreference resolution framework 
components and the linguistic components they rely on, as well as the 
coreference resolution pipelines that were used to evaluate the tool. These 
components are implemented in Java and are availabe from the URL: 
https://github.com/kilicogluh/Bio-SCoRes.


------------------------
Prerequisites
------------------------
- Java (jar files have been generated with 1.8, though it is possible to
recompile with 1.7)
- Ant (needed for recompilation, version 1.8 was used for compilation)

------------------------
Directory Structure
------------------------
bin directory contains scripts to run sample coreference resolution
pipelines and evaluate them.

dist directory contains libraries distributed with Bio-SCoRes. These are
the following:

- ling.jar: Contains the core linguistic components used by Bio-SCoRes.
- bioscores.jar: Contains the coreference resolution components.
- coreftasks.jar: Contains the coreference resolution pipelines.

To use Bio-SCoRes from your application, ensure that the first two jar files
are included in your classpath. The third jar file (coreftasks.jar) is
required if you plan to use/adapt the example coreference resolution pipelines
described in the PLOS ONE article.

DATA directory contains the SPL drug coreference corpus and various input and
output files derived from it, as well input and output files for the BioNLP
protein coreference dataset experiments.

lib directory contains third-party libraries required by the system (see Note
regarding Stanford Core NLP below.)

resources directory contains WordNet dictionary files that are required by 
the system.

The top level directory contains ant build file as well as properties files
used by the pipelines.

- coref_spl.properties:		Properties for the SPL pipelines.
- coref_bionlp.properties:	Properties for the BioNLP pipeline.
- coref.properties:		Properties for the generic pipeline.
- build.xml:			Ant build file for all components.

------------------------
Usage
------------------------
If you're only interested in seeing the capabilities of Bio-SCoRes, a good
starting point is to run the scripts provided in the bin directory, which 
correspond to the pipelines described in the PLOS ONE article and use
the data provided. These scripts do not require any arguments and point to
existing input and output directories under DATA. They can be modified to
fit individual needs (see the Note below regarding i2b2/VA corpus). 

- splGoldMentions:	The script for running coreference resolution on 
			structured drug label (SPL) dataset with gold 
			coreferential mentions. In other words, only 
			mention-referent linking is performed.

- splGoldEntities: 	The script for running coreference resolution on 
			structured drug label (SPL) dataset with gold 
			named entities. This pipeline consists of 
			coreferential mention detection and mention-referent
			linking.

- bionlp:		The script for running anaphora/appositive resolution
			on BioNLP'11 Shared Task coreference dataset. This 
			pipeline consists of mention detection and mention-
			referent linking.

- genericPipeline:	The script for running a generic pipeline. Input to 
			this script is an input directory with text files and
			corresponding term annotations in brat standoff 
			format and an output directory, where the results are
			written in the same standoff format. Note that to be 
			effective, the term-related domain knowledge should 
			have been introduced to the system as properties 
			(See the Note below). 
 
The evaluation scripts in the same directory (*Evaluate) can be used to
evaluate the output generated by the system using one of the aforementioned
scripts. For example, splGoldMentionsEvaluate script evaluates the results
generated using splGoldMentions. The evaluation script will calculate
precision, recall, and F1. In addition, true positives, false positives and
negatives can be printed out. To ensure the integrity of the distribution,
you can compare the the evaluation results generated with those in Evaluation
directory to ensure that they match. 

If you're interested in incorporating Bio-SCoRes into your NLP pipeline, a 
good starting point is the source code for the pipelines (each in its
respective subpackage in tasks.coref.* package). 


- SPLCoreferencePipeline:	The entry point for splGoldMentions and 
				splGoldEntities scripts. It makes the most 
				extensive use of the framework (all mention 
				and coreference types).

- BioNLPCoreferencePipeline: 	The entry point for bionlp script. This is 
				the simplest pipeline and only performs 
				anaphora and appositive resolution. 		

The generic pipeline mentioned above uses the corresponding class 
gov.nih.nlm.bioscores.core.GenericCoreferencePipeline. 

To adapt a pipeline to your needs, it makes most sense to start with adapting 
loadStrategies() method where the coreference resolution strategies are 
defined. Components of each strategy are implemented in various 
gov.nih.nlm.bioscores.* subpackages. New components can also be defined by 
implementing interfaces such as Agreement, CandidateFilter, 
PostScoringCandidateFilter. Modifying the post-processing steps to tailor
the coreference links generated by the system is the logical next step;
postProcessing() method of the pipeline class deals with this task.

--------------------------------
Note on Named Entity Recognition
--------------------------------			
Bio-SCoRes does not provide a named entity recognition module. However, for 
it to have some degree of success, it requires that terms (drugs, disorders, 
etc.) in the text have already been labeled and semantic types/groups that are
relevant to the task have been introduced to the framework via java properties. 
You can examine coref*.properties files at the top level directory
to get a feel for how semantic information can be defined within the framework.
For example, coref_spl.properties contains mostly drug-related settings used 
by the SPL pipelines, while coref_bionlp.properties includes gene/protein 
related settings. Note that, in addition to semantic types/groups, these 
configuration files may define word lists for hypernyms (i.e., high level
terms) and event triggers (for example, 'phosphorylation' for gene/proteins)
of relevant semantic groups, which can be exploited by the Agreement methods
of the framework. In their absence, the system can still generate results,
but the performance is likely to suffer. See the PLOS ONE article for more
details.

--------------------------------
Note on the i2b2/VA corpus
--------------------------------
Due to usage restrictions, we do not include data derived from the i2b2/VA 
corpus and experiment results based on this corpus. The corpus can 
be obtained from http://www.i2b2.org/NLP/Coreference and the XML input 
expected by the i2b2 pipeline can be generated using the tasks.coref.i2b2.
I2B2ToXMLWriter class included. I2B2CoreferencePipeline in the same package
can be used to perform coreference resolution on this input. The i2b2 pipeline
reported in the paper was evaluated using the i2b2 coreference scorer, which 
can be obtained from the same URL.

--------------------------------
Note on Stanford CoreNLP package
--------------------------------
Stanford CoreNLP model jar file that is needed for processing raw text
for lexical and syntactic information (stanford-corenlp-3.3.1-models.jar) is 
not included with the distribution due to its size. It can be downloaded from 
http://stanfordnlp.github.io/CoreNLP/ and copied to lib directory.


------------------------
DEVELOPER
------------------------

Halil Kilicoglu


---------
CONTACT
---------

- Halil Kilicoglu:      kilicogluh@mail.nih.gov


---------
WEBPAGE
---------

A Bio-SCoRes webpage is available with all up-to-date instructions, code, 
and pipelines.

https://github.com/kilicogluh/Bio-SCoRes

---------------------------------------------------------------------------
