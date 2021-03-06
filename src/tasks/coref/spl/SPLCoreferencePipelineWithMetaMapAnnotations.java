package tasks.coref.spl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.nih.nlm.bioscores.agreement.AnimacyAgreement;
import gov.nih.nlm.bioscores.agreement.DiscourseConnectiveAgreement;
import gov.nih.nlm.bioscores.agreement.GenderAgreement;
import gov.nih.nlm.bioscores.agreement.HypernymListAgreement;
import gov.nih.nlm.bioscores.agreement.NumberAgreement;
import gov.nih.nlm.bioscores.agreement.PersonAgreement;
import gov.nih.nlm.bioscores.agreement.PredicateNominativeAgreement;
import gov.nih.nlm.bioscores.agreement.SemanticCoercionAgreement;
import gov.nih.nlm.bioscores.agreement.SemanticGroupAgreement;
import gov.nih.nlm.bioscores.agreement.SemanticTypeAgreement;
import gov.nih.nlm.bioscores.agreement.SyntacticAppositiveAgreement;
import gov.nih.nlm.bioscores.candidate.CandidateFilter;
import gov.nih.nlm.bioscores.candidate.CandidateSalience;
import gov.nih.nlm.bioscores.candidate.DefaultCandidateFilter;
import gov.nih.nlm.bioscores.candidate.ExemplificationFilter;
import gov.nih.nlm.bioscores.candidate.PostScoringCandidateFilter;
import gov.nih.nlm.bioscores.candidate.PostScoringCandidateFilterImpl;
import gov.nih.nlm.bioscores.candidate.PriorDiscourseFilter;
import gov.nih.nlm.bioscores.candidate.SubsequentDiscourseFilter;
import gov.nih.nlm.bioscores.candidate.SyntaxBasedCandidateFilter;
import gov.nih.nlm.bioscores.candidate.WindowSizeFilter;
import gov.nih.nlm.bioscores.core.Configuration;
import gov.nih.nlm.bioscores.core.CoreferenceChain;
import gov.nih.nlm.bioscores.core.CoreferenceSemanticItemFactory;
import gov.nih.nlm.bioscores.core.CoreferenceType;
import gov.nih.nlm.bioscores.core.Expression;
import gov.nih.nlm.bioscores.core.ExpressionType;
import gov.nih.nlm.bioscores.core.GenericCoreferencePipeline;
import gov.nih.nlm.bioscores.core.ScoringFunction;
import gov.nih.nlm.bioscores.core.Strategy;
import gov.nih.nlm.bioscores.exp.ExpressionFilter;
import gov.nih.nlm.bioscores.exp.ExpressionFilterImpl;
import gov.nih.nlm.ling.brat.StandoffAnnotationWriter;
import gov.nih.nlm.ling.core.Document;
import gov.nih.nlm.ling.io.XMLEntityReader;
import gov.nih.nlm.ling.io.XMLReader;
import gov.nih.nlm.ling.process.ComponentLoader;
import gov.nih.nlm.ling.process.SectionSegmenter;
import gov.nih.nlm.ling.sem.DomainProperties;
import gov.nih.nlm.ling.sem.Entity;
import gov.nih.nlm.ling.sem.SemanticItem;
import gov.nih.nlm.ling.sem.Term;
import gov.nih.nlm.ling.util.FileUtils;
import gov.nih.nlm.ling.wrappers.WordNetWrapper;

/**
 * The coreference resolution pipeline for SPL drug coreference dataset,
 * that uses entities extracted by MetaMap as input.
 * It can use the resolution strategies that were optimized for gold entities or
 * the strategies that were optimized for MetaMap output.
 * 
 * @author Halil Kilicoglu
 *
 */
public class SPLCoreferencePipelineWithMetaMapAnnotations {
	private static Logger log = Logger.getLogger(SPLCoreferencePipelineWithMetaMapAnnotations.class.getName());
	
	private static SectionSegmenter sectionSegmenter = null;
	private static boolean useGoldStrategies = false;
	
	/**
	 * Loads the resolution strategies that yield the best results on entities generated by MetaMap.
	 * 
	 * @return	the list of resolution strategies.
	 */
	public static List<Strategy> loadBestStrategies() {
		List<Strategy> defs = new ArrayList<>();
		
		ExpressionFilterImpl expressionFilterImpl = new ExpressionFilterImpl();
		List<ExpressionFilter> anaphoricExpFilters = new ArrayList<>();
		anaphoricExpFilters.add(expressionFilterImpl.new AnaphoricityFilter());
		List<ExpressionFilter> personalPrExpFilters = new ArrayList<>();
		personalPrExpFilters.add(expressionFilterImpl.new ThirdPersonPronounFilter());
		List<ExpressionFilter> relExpFilters = new ArrayList<>();
		relExpFilters.add(expressionFilterImpl.new NonCorefRelativePronFilter());
		
		List<CandidateFilter> anaphoraFilters = new ArrayList<>();
		anaphoraFilters.add(new PriorDiscourseFilter());
		anaphoraFilters.add(new WindowSizeFilter(Configuration.RESOLUTION_WINDOW_ALL));
		anaphoraFilters.add(new SyntaxBasedCandidateFilter());
		anaphoraFilters.add(new DefaultCandidateFilter());
		anaphoraFilters.add(new ExemplificationFilter());
		
		List<CandidateFilter> possFilters = new ArrayList<>();
		possFilters.add(new PriorDiscourseFilter());
		possFilters.add(new WindowSizeFilter(2));
		possFilters.add(new DefaultCandidateFilter());
		possFilters.add(new ExemplificationFilter());
		
		List<CandidateFilter> pronounFilters = new ArrayList<CandidateFilter>();
		pronounFilters.add(new PriorDiscourseFilter());
		pronounFilters.add(new WindowSizeFilter(2));
		pronounFilters.add(new SyntaxBasedCandidateFilter());
		pronounFilters.add(new DefaultCandidateFilter());
		pronounFilters.add(new ExemplificationFilter());
		
		PostScoringCandidateFilterImpl postScoringFilterImpl = new PostScoringCandidateFilterImpl();
		List<PostScoringCandidateFilter> postScoringFilters = new ArrayList<>();
		postScoringFilters.add(postScoringFilterImpl.new ThresholdFilter(4));
		postScoringFilters.add(postScoringFilterImpl.new TopScoreFilter());
			
		List<PostScoringCandidateFilter> parseTreeFilter = new ArrayList<>(postScoringFilters);
		parseTreeFilter.add(postScoringFilterImpl.new SalienceTypeFilter(CandidateSalience.Type.ParseTree));

		List<PostScoringCandidateFilter> proximityFilter = new ArrayList<>(postScoringFilters);
		proximityFilter.add(postScoringFilterImpl.new SalienceTypeFilter(CandidateSalience.Type.Proximity));
		
		List<ScoringFunction> pronounScoringFunction = new ArrayList<>();
		pronounScoringFunction.add(new ScoringFunction(AnimacyAgreement.class,1,0));
		pronounScoringFunction.add(new ScoringFunction(GenderAgreement.class,1,0));
		pronounScoringFunction.add(new ScoringFunction(NumberAgreement.class,1,0));
		pronounScoringFunction.add(new ScoringFunction(PersonAgreement.class,1,0));
		
		List<ScoringFunction> possPronounScoringFunction = new ArrayList<>(pronounScoringFunction);
		possPronounScoringFunction.add(new ScoringFunction(SemanticCoercionAgreement.class,1,0));
		
		List<ScoringFunction> nominalScoringFunction = new ArrayList<>();
		nominalScoringFunction.add(new ScoringFunction(NumberAgreement.class,1,1));
		nominalScoringFunction.add(new ScoringFunction(HypernymListAgreement.class,3,1));
//		nominalScoringFunction.add(new ScoringFunction(TaxonomyAgreement.class,4,0));

		defs.add(new Strategy(CoreferenceType.Anaphora,ExpressionType.PersonalPronoun,
				personalPrExpFilters,pronounFilters,pronounScoringFunction,parseTreeFilter));
		defs.add(new Strategy(CoreferenceType.Anaphora,ExpressionType.PossessivePronoun,
				personalPrExpFilters,possFilters,possPronounScoringFunction,parseTreeFilter));
		defs.add(new Strategy(CoreferenceType.Anaphora,ExpressionType.DistributivePronoun,
				null,pronounFilters,pronounScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Anaphora,ExpressionType.ReciprocalPronoun,
				null,pronounFilters,pronounScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Anaphora,ExpressionType.DefiniteNP,
				anaphoricExpFilters,anaphoraFilters,nominalScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Anaphora,ExpressionType.DemonstrativeNP,
				anaphoricExpFilters,anaphoraFilters,nominalScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Anaphora,ExpressionType.DistributiveNP,
				anaphoricExpFilters,anaphoraFilters,nominalScoringFunction,proximityFilter));
			
		List<ExpressionFilter> cataphoricExpFilters = new ArrayList<>();
		cataphoricExpFilters.add(expressionFilterImpl.new CataphoricityFilter());
		
		List<CandidateFilter> cataphoraFilters = new ArrayList<>();
		cataphoraFilters.add(new SubsequentDiscourseFilter());
		cataphoraFilters.add(new WindowSizeFilter(2));
		cataphoraFilters.add(new SyntaxBasedCandidateFilter());
		cataphoraFilters.add(new DefaultCandidateFilter());
		
		List<CandidateFilter> cataphoraPersPronFilters = new ArrayList<>();
		cataphoraPersPronFilters.add(new SubsequentDiscourseFilter());
		cataphoraPersPronFilters.add(new WindowSizeFilter(Configuration.RESOLUTION_WINDOW_SENTENCE));
		cataphoraPersPronFilters.add(new SyntaxBasedCandidateFilter());
		cataphoraPersPronFilters.add(new DefaultCandidateFilter());
		
		List<CandidateFilter> cataphoraPossFilters = new ArrayList<>();
		cataphoraPossFilters.add(new SubsequentDiscourseFilter());
		cataphoraPossFilters.add(new WindowSizeFilter(Configuration.RESOLUTION_WINDOW_SENTENCE));
		cataphoraPossFilters.add(new DefaultCandidateFilter());
		
		List<ScoringFunction> catPronounScoringFunction = new ArrayList<>(pronounScoringFunction);
		catPronounScoringFunction.add(new ScoringFunction(DiscourseConnectiveAgreement.class,1,2));
		
		List<ScoringFunction> catNPScoringFunction = new ArrayList<ScoringFunction>();
		catNPScoringFunction.add(new ScoringFunction(NumberAgreement.class,1,1));
		catNPScoringFunction.add(new ScoringFunction(SemanticGroupAgreement.class,1,1));
		catNPScoringFunction.add(new ScoringFunction(SemanticTypeAgreement.class,2,0));
		catNPScoringFunction.add(new ScoringFunction(HypernymListAgreement.class,3,0));

		defs.add(new Strategy(CoreferenceType.Cataphora,ExpressionType.PersonalPronoun,
				personalPrExpFilters,cataphoraPersPronFilters,catPronounScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Cataphora,ExpressionType.PossessivePronoun,
				personalPrExpFilters,cataphoraPossFilters,catPronounScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Cataphora,ExpressionType.DefiniteNP,
				cataphoricExpFilters,cataphoraFilters,catNPScoringFunction,proximityFilter));
		
		List<CandidateFilter> apposFilters = new ArrayList<>();
		apposFilters.add(new WindowSizeFilter(Configuration.RESOLUTION_WINDOW_SENTENCE));
		apposFilters.add(new DefaultCandidateFilter());
		List<ScoringFunction> apposScoringFunction = new ArrayList<>();
		apposScoringFunction.add(new ScoringFunction(NumberAgreement.class,1,1));
		apposScoringFunction.add(new ScoringFunction(SemanticGroupAgreement.class,1,1));
		apposScoringFunction.add(new ScoringFunction(SemanticTypeAgreement.class,2,0));
		apposScoringFunction.add(new ScoringFunction(SyntacticAppositiveAgreement.class,3,2));
		
		defs.add(new Strategy(CoreferenceType.Appositive,ExpressionType.DefiniteNP,
				null,apposFilters,apposScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Appositive,ExpressionType.IndefiniteNP,
				null,apposFilters,apposScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.Appositive,ExpressionType.ZeroArticleNP,
				null,apposFilters,apposScoringFunction,proximityFilter));

		List<CandidateFilter> predNomFilters = new ArrayList<>(apposFilters);
		predNomFilters.add(new PriorDiscourseFilter());
		predNomFilters.add(new WindowSizeFilter(Configuration.RESOLUTION_WINDOW_SENTENCE));
		predNomFilters.add(new DefaultCandidateFilter());
		
		List<ScoringFunction> predNomScoringFunction = new ArrayList<>();
		predNomScoringFunction.add(new ScoringFunction(NumberAgreement.class,1,1));
		predNomScoringFunction.add(new ScoringFunction(SemanticGroupAgreement.class,1,0));
		predNomScoringFunction.add(new ScoringFunction(SemanticTypeAgreement.class,2,0));
		predNomScoringFunction.add(new ScoringFunction(PredicateNominativeAgreement.class,3,2));
		predNomScoringFunction.add(new ScoringFunction(HypernymListAgreement.class,1,1));
		
		defs.add(new Strategy(CoreferenceType.PredicateNominative,ExpressionType.IndefiniteNP,
				null,predNomFilters,predNomScoringFunction,proximityFilter));
		defs.add(new Strategy(CoreferenceType.PredicateNominative,ExpressionType.ZeroArticleNP,
				null,predNomFilters,predNomScoringFunction,proximityFilter));
		
		return defs;
	}
	
	/**
	 * Writes relevant semantic objects as standoff annotations to an output file.
	 * Differently from other similar methods, it prints out concept information
	 * generated by MetaMap, as well.
	 * 
	 * @param writeTypes	the semantic object types to write
	 * @param outFileName	the output file to write to
	 * @param doc			the document that contains the semantic objects
	 * @throws IOException	if <var>outFileName</var> cannot be opened
	 */
	public static void writeStandoffAnnotations(List<Class<? extends SemanticItem>> writeTypes, String outFileName, Document doc)
		throws IOException {
		List<String> writeLines = new ArrayList<>();
		int refId = 0;
		for (SemanticItem si: doc.getAllSemanticItems()) {
			if (writeTypes.contains(si.getClass()) == false) continue;
			if (si instanceof Expression) {
				if (SPLCoreferencePipeline.toWrite((Expression)si) == false) continue; 
				writeLines.add(si.toStandoffAnnotation());
			}
			else if (si instanceof CoreferenceChain) {
				CoreferenceChain cc = (CoreferenceChain)si;
				writeLines.addAll(SPLCoreferencePipeline.coreferenceChainToSPLStandoffAnnotation(cc));
			} 	else if (si instanceof Entity) {
				String standoff = ((Entity) si).toStandoffAnnotation(true,refId);
				writeLines.add(standoff);
				refId += standoff.split("[\\n]+").length-1;
			} else 
				writeLines.add(si.toStandoffAnnotation());
		}
		StandoffAnnotationWriter.writeLines(writeLines,outFileName);
	}
	
	/**
	 * Reads properties from properties file and initializes
	 * WordNet, section segmenter, and coreference-related word lists. 
	 * It also selects the resolution strategies to use and loads them.
	 * 
	 * @throws ClassNotFoundException	if the section segmenter class cannot be found
	 * @throws IllegalAccessException	if the section segmenter cannot be accessed
	 * @throws InstantiationException	if the section segmenter cannot be initialized
	 * @throws IOException				if properties file cannot be found
	 */
	public static void init() 
			throws IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {		
		Properties props = FileUtils.loadPropertiesFromFile("coref_spl.properties");
		props.put("sectionSegmenter", "tasks.coref.spl.SPLSectionSegmenter");
		DomainProperties.init(props);
		WordNetWrapper.getInstance(props);
		if (useGoldStrategies) 
			Configuration.getInstance(SPLCoreferencePipeline.loadSPLStrategies());
		else 
			Configuration.getInstance(loadBestStrategies());			
		sectionSegmenter = ComponentLoader.getSectionSegmenter(props);
//		Concept.loadHierarchy("resources/spl_hierarchy");
	}
	
	public static void main(String[] args) throws IOException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		if (args.length < 2) {
			System.err.print("Usage: inputDirectory outputDirectory [useGoldStrategies]");
		}
		String in = args[0];
		String a2Out = args[1];
		
		File inDir = new File(in);
		if (inDir.isDirectory() == false) {
			System.err.println("First argument is required to be an input directory:" + in);
			System.exit(1);
		}
		File a2OutDir = new File(a2Out);
		if (a2OutDir.isDirectory() == false) {
			System.err.println("The directory " + a2Out + " doesn't exist. Creating a new directory..");
			a2OutDir.mkdir();
		}
		// whether to use the strategies that yield the best results with gold entity/mentions
		if (args.length >2) {
			useGoldStrategies = Boolean.parseBoolean(args[2]);
		}
		// initialize 
		init();
					
		// annotations to load
		Map<Class<? extends SemanticItem>,List<String>> annTypes = new HashMap<>();
		List<String> parseTypes = DomainProperties.parse2("annotationTypes").get("Entity");
		annTypes.put(Entity.class,parseTypes);
		XMLReader reader = new XMLReader();
		reader.addAnnotationReader(Entity.class, new XMLEntityReader());
		
		// iterate through files
		List<String> files = FileUtils.listFiles(in, false, "xml");
		if (files.size() == 0) log.log(Level.SEVERE, "No XML file found in input directory {0}.", new Object[]{in});
		int fileNum = 0;
		for (String filename: files) {
			String filenameNoExt = filename.replace(".xml", "");
			filenameNoExt = filenameNoExt.substring(filenameNoExt.lastIndexOf(File.separator)+1);
			log.log(Level.INFO,"Processing file {0}: {1}.", new Object[]{filenameNoExt,++fileNum});
			String a2Filename = a2OutDir.getAbsolutePath() + File.separator + filenameNoExt + ".ann";
			Document doc = reader.load(filename, true, CoreferenceSemanticItemFactory.class,annTypes, null);
			LinkedHashSet<SemanticItem> terms = Document.getSemanticItemsByClass(doc, Term.class);
			if (terms != null) {
				for (SemanticItem ent: terms) 
					log.log(Level.FINE,"Loaded term: {0}." , ((Term)ent).toString());
			}
			
			// for the most part, use the other pipeline
			GenericCoreferencePipeline.linguisticPreProcessing(doc);
			SPLCoreferencePipeline.domainSpecificPreProcessing(doc,sectionSegmenter);
			// coreference
			SPLCoreferencePipeline.coreferenceResolution(doc);
			// write
			List<Class<? extends SemanticItem>> writeTypes = new ArrayList<Class<? extends SemanticItem>>(annTypes.keySet());
			writeTypes.add(Expression.class);
			writeTypes.add(CoreferenceChain.class);
			writeStandoffAnnotations(writeTypes,a2Filename,doc);
		}
		
	}
}
