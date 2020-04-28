package org.rtassembly.npgraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;


import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import japsa.seq.PAFRecord;
import japsa.seq.Sequence;
import japsa.seq.SequenceReader;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
public class HybridAssembler {
    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	//setting parameter for the GUI
    private boolean ready=false;
    private BooleanProperty overwrite, useSPAdesPath;
    private StringProperty 	prefix,
    						aligner,
    						msa,
							alignerOpt,
							shortReadsInput, 
							binReadsInput, 
							longReadsInput,
							shortReadsInputFormat, 
							longReadsInputFormat;
	
	Process alignmentProcess = null;
	private boolean stop=false;
	private String checkLog="";
	public int currentReadCount = 0;
	public long currentBaseCount = 0;	
	//Getters and Setters
	//==============================================================================================//
	public void setReady(boolean isReady) {ready=isReady;}
	public boolean getReady() {return ready;}
	
	
	public final void setOverwrite(boolean owr) {overwrite.set(owr);}
	public final boolean getOverwrite() {return overwrite.get();}
	public BooleanProperty overwriteProperty() {return overwrite;}
	
	public final void setUseSPAdesPath(boolean owr) {useSPAdesPath.set(owr);}
	public final boolean getUseSPAdesPath() {return useSPAdesPath.get();}
	public BooleanProperty useSPAdesPathProperty() {return useSPAdesPath;}
	
	public final void setPrefix(String output) {prefix.set(output);}
	public final String getPrefix() {return prefix.get();}
	public StringProperty prefixProperty() {return prefix;}
	
	public final void setAligner(String tool) {	aligner.set(tool);}
	public final String getAligner() {return aligner.get();}
	public StringProperty alignerProperty(){return aligner;}
	
	public final void setAlignerOpts(String setting) {alignerOpt.set(setting);}
	public final String getAlignerOpts() {return alignerOpt.get();}
	public StringProperty alignerOptProperty(){return alignerOpt;}
	
	public final void setMSA(String tool) {	msa.set(tool);}
	public final String getMSA() {return msa.get();}
	public StringProperty msaProperty(){return msa;}
	
	public final void setCheckLog(String log) {checkLog=(log==null?null:checkLog+"\n"+log);}
	public final String getCheckLog() {return checkLog;}
		
	public final void setBinReadsInput(String brInput) {binReadsInput.set(brInput);}
	public final String getBinReadsInput() {return binReadsInput.get();}
	public StringProperty binReadsInputProperty() {return binReadsInput;}
	
	public final void setShortReadsInput(String srInput) {
		shortReadsInput.set(srInput);
	}
	public final String getShortReadsInput() {return shortReadsInput.get();}
	public StringProperty shortReadsInputProperty() {return shortReadsInput;}
	
	public final void setLongReadsInput(String lrInput) {
		longReadsInput.set(lrInput);
	}
	public final String getLongReadsInput() {return longReadsInput.get();}
	public StringProperty longReadsInputProperty() {return longReadsInput;}
	
	public final void setShortReadsInputFormat(String srInputFormat) {shortReadsInputFormat.set(srInputFormat);}
	public final String getShortReadsInputFormat() {return shortReadsInputFormat.get();}
	public StringProperty shortReadsInputFormatProperty() {return shortReadsInputFormat;}
	
	public final void setLongReadsInputFormat(String lrInputFormat) {
		lrInputFormat=lrInputFormat.toLowerCase();
		if(	lrInputFormat.contains("fasta") || lrInputFormat.contains("fa") || lrInputFormat.contains("fna")
			|| lrInputFormat.contains("fastq") || lrInputFormat.contains("fq"))
			longReadsInputFormat.set("fasta/fastq");
		else if(lrInputFormat.contains("sam") || lrInputFormat.contains("bam"))
			longReadsInputFormat.set("sam/bam");
		else if(lrInputFormat.contains("paf"))
			longReadsInputFormat.set("paf");
	}
	public final String getLongReadsInputFormat() {return longReadsInputFormat.get();}
	public StringProperty longReadsInputFormatProperty() {return longReadsInputFormat;}
	
	public synchronized void setStopSignal(boolean stop) {this.stop=stop;}
	public synchronized boolean getStopSignal() {return stop;}
	//===============================================================================================//
	
	//Operational variables
	public volatile BDGraph simGraph; //original and simplified graph should be separated, no???
	public RealtimeGraphWatcher observer;
	
	public HybridAssembler(){
//		origGraph=new BDGraph("batch");
		simGraph=new BDGraph("real");
//		rtComponents = new ConnectedComponents();
		simGraph.setAttribute("ui.quality");
		simGraph.setAttribute("ui.antialias");
		
		overwrite = new SimpleBooleanProperty(true);
		useSPAdesPath = new SimpleBooleanProperty(false);
	    prefix = new SimpleStringProperty(System.getProperty("java.io.tmpdir"));
	    aligner = new SimpleStringProperty("");
		alignerOpt = new SimpleStringProperty("");
		msa = new SimpleStringProperty("kalign");
		
		shortReadsInput = new SimpleStringProperty(""); 
		binReadsInput = new SimpleStringProperty(""); 
		longReadsInput = new SimpleStringProperty("");
		shortReadsInputFormat = new SimpleStringProperty(""); 
		longReadsInputFormat = new SimpleStringProperty("");
		
		//set all binding options here...
        shortReadsInput.addListener((observable, oldValue, newValue) -> 
			{
				String fn = ((String)observable.getValue()).toLowerCase();
				if(	fn.endsWith(".fastg")) 
					setShortReadsInputFormat("fastg");
				else if(fn.endsWith(".gfa"))
					setShortReadsInputFormat("gfa");
			}	 

        );
        
        shortReadsInputFormat.addListener((observable, oldValue, newValue) -> 
			{
				if(!getShortReadsInput().toLowerCase().endsWith(newValue))
					setShortReadsInput("");
			}	 

        );
		
        longReadsInput.addListener( (observable, oldValue, newValue) -> 
    		{
				String fn = ((String)observable.getValue()).toLowerCase();
				if(	fn.endsWith(".fasta") || fn.endsWith(".fa") || fn.endsWith("fna")
					|| fn.endsWith(".fastq") || fn.endsWith(".fq")
					|| fn.endsWith(".fasta.gz") || fn.endsWith(".fa.gz") || fn.endsWith("fna.gz")
					|| fn.endsWith(".fastq.gz") || fn.endsWith(".fq.gz") 
					) 
					setLongReadsInputFormat("fasta/fastq");
				else if(fn.endsWith(".sam") || fn.endsWith(".bam")) 
					setLongReadsInputFormat("sam/bam");
				else if(fn.endsWith(".paf")) 
					setLongReadsInputFormat("paf");	
    		}	 
        );
        
        longReadsInputFormat.addListener((observable, oldValue, newValue) -> 
			{
				String oldFile=getLongReadsInput().toLowerCase();
				if(oldFile.equals("-"))
					return;
				if(	newValue.equals("fasta/fastq") 
							&& !oldFile.endsWith(".fasta") && !oldFile.endsWith(".fa") && !oldFile.endsWith("fna")
							&& !oldFile.endsWith(".fastq") && !oldFile.endsWith(".fq")
							&& !oldFile.endsWith(".fasta.gz") && !oldFile.endsWith(".fa.gz") && !oldFile.endsWith("fna.gz")
							&& !oldFile.endsWith(".fastq.gz") && !oldFile.endsWith(".fq.gz") 
							) 
					setLongReadsInput("");
						
				if(newValue.equals("sam/bam") && !oldFile.endsWith(".sam") && !oldFile.endsWith(".bam"))
					setLongReadsInput("");
				
				if(newValue.equals("paf") && !oldFile.endsWith(".paf"))
					setLongReadsInput("");
			}	 

        );
        
        aligner.addListener( (observable, oldValue, newValue) ->
        	{
				String aligner=(String)observable.getValue();
				if(aligner.toLowerCase().endsWith("minimap2"))
					setAlignerOpts("-t4 -k15 -w5");
				else if (aligner.toLowerCase().endsWith("bwa"))
					setAlignerOpts("-t4 -k11 -W20 -r10 -A1 -B1 -O1 -E1 -L0 -a -Y");			
			}	 

        );
        

	}
	
    private boolean checkFile(String _path) {
		if (_path.equals("")){
			setCheckLog("Empty file \"" + _path + "\"");
			return false;
		}
		File _file = new File(_path);
		if (!_file.isFile()){
			setCheckLog("File \"" + _path + "\" is not valid!");
			return false;
		}
		return true;
    }
    private boolean checkFolder(String _path) {
		if (_path.equals("")){
			setCheckLog("Empty directory \"" + _path + "\"");
			return false;
		}
		File _file = new File(_path);
		if (!_file.isDirectory()){
			setCheckLog("Directory \"" + _path + "\" is not valid!");
			return false;
		}
		return true;
    }	
	
	//Indexing reference, prepare for alignment...r
	public boolean prepareLongReadsProcess(){
		//accept the case when no long read data is provided. Just output simplified assembly graph then.
		if(getLongReadsInput().isEmpty()) {
			logger.warn("No long read data is provided. Only output the simplified assembly graph and stop!");
			return true;
		}
		
		if(!getLongReadsInputFormat().equals("fasta/fastq") 
			&& !getLongReadsInputFormat().equals("sam/bam")
			&& !getLongReadsInputFormat().equals("paf") ){
			setCheckLog("Please specify a correct format of long read data (FASTA/FASTQ/BAM/SAM/PAF)!");
			return false;
		}
		if(!getLongReadsInput().equals("-") && !checkFile(getLongReadsInput()))
			return false;
		
		if(!checkFolder(getPrefix()))
			return false;
		try{
			System.setProperty("usr.dir", getPrefix());			
		}
		catch(NullPointerException | IllegalArgumentException | SecurityException exception ){
			setCheckLog("Fail to set working directory usr.dir to " + getPrefix());
			return false;
		}
		
		//create temporary folder to store bridging reads
		File tmpFolder=new File(getPrefix()+File.separator+"npGraph_tmp");
		if(tmpFolder.exists()){
		  try {
			Files.walk(tmpFolder.toPath())
			    .sorted(Comparator.reverseOrder())
			    .map(Path::toFile)
			    .forEach(File::delete);
			} catch (IOException e) {
				logger.error("Cannot remove existed temporary folder: {}", e);
			}
  		}
		
		if(!tmpFolder.mkdir()){
			setCheckLog("Cannot set temporary folder " + tmpFolder.getAbsolutePath());
			return false;
		}else{
			AlignedRead.tmpFolder=tmpFolder.getAbsolutePath();
		}
		
		//if long reads data not given in SAM/BAM, need to invoke minimap2
        if(getLongReadsInputFormat().contains("fast")) {
        	File indexFile=null;
        	ArrayList<String> idxCmd = new ArrayList<>();
        	idxCmd.add(getAligner());
        	if(getAligner().endsWith("minimap2")) { 	
				indexFile=new File(getPrefix()+File.separator+"assembly_graph.mmi");												
				if(!checkMinimap2()) 
						return false;
				idxCmd.addAll(Arrays.asList(getAlignerOpts().split("\\s")));
				idxCmd.add("-d");
				idxCmd.add(getPrefix()+File.separator+"assembly_graph.mmi");
														
        	}else if(getAligner().endsWith("bwa")) {
				indexFile=new File(getPrefix()+File.separator+"assembly_graph.fasta.bwt");
				if(!checkBWA()) 
						return false;
				idxCmd.add("index");

        	}else {
        		setCheckLog("Invalid aligner! Set to BWA or minimap2 please!");
        		return false;
        	}
			idxCmd.add(getPrefix()+File.separator+"assembly_graph.fasta");

			if(getOverwrite() || !indexFile.exists()) {						
				try{
					simGraph.outputFASTA(getPrefix()+File.separator+"assembly_graph.fasta");
					
					ProcessBuilder pb = new ProcessBuilder(idxCmd);
					Process indexProcess =  pb.start();
					indexProcess.waitFor();
					
				}catch (IOException | InterruptedException e){
					setCheckLog("Issue when indexing the pre-assemblies: \n" + e.getMessage());
					return false;
				}
			}
			
        }
        
        //check consensus tool
    	if(!checkMSA()){
    		setCheckLog("WARNING: MSA tool (" + getMSA() + ") not found!");
    		setMSA("none");
    		logger.warn("WARNING: MSA tools not found!");
    	}else
    		logger.info("MSA for consensus calling is set to " + getMSA());
    	
		simGraph.consensus.setConsensusMSA(getMSA());
    	
        return true;
	}
	//Loading the graph, doing preprocessing
	//binning, ...
	public boolean prepareShortReadsProcess() {
		if(!getShortReadsInputFormat().equals("fastg") && !getShortReadsInputFormat().equals("gfa")){
			setCheckLog("Please specify a correct format of graph file!");
			return false;
		}
			
		if(!checkFile(getShortReadsInput()))
			return false;
		
		//try to read input file
		try {
			if(getShortReadsInputFormat().toLowerCase().equals("gfa")) 
				GraphUtil.loadFromGFA(getShortReadsInput(), getBinReadsInput(), simGraph, getUseSPAdesPath());
			else if(getShortReadsInputFormat().toLowerCase().equals("fastg"))
				GraphUtil.loadFromFASTG(getShortReadsInput(), getBinReadsInput(), simGraph, getUseSPAdesPath());
			else 				
				throw new IOException("Assembly graph file must have .gfa or .fastg extension!");
			
		}catch(IOException e) {
			setCheckLog("Issue when loading pre-assembly: \n" + e.getMessage());
			return false;
		}
		
		simGraph.updateStats();
		observer = new RealtimeGraphWatcher(this);
		return true;
	}

	/**
	 * SHN modified the default aligner to minimap2
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void assembly() 
			throws IOException, InterruptedException{
		int timeInterval=(int) (Math.round(Math.log10(simGraph.getNodeCount()))-1); //estimated interval time based on graph complexity
		timeInterval=(timeInterval>1?timeInterval:1)*10;
		int readInterval=100;
		
		observer.setReadPeriod(RealtimeGraphWatcher.R_INTERVAL!=0?RealtimeGraphWatcher.R_INTERVAL:readInterval);
		observer.setTimePeriod((RealtimeGraphWatcher.T_INTERVAL!=0?RealtimeGraphWatcher.T_INTERVAL:timeInterval) * 1000);

		if(getLongReadsInput().isEmpty()) {
//			LOG.info("Scaffolding is ignored due to lack of long-read input!");
			return;
		}else
			logger.info("Scaffolding ready at " + new Date());

		SamReaderFactory.setDefaultValidationStringency(ValidationStringency.SILENT);
		SamReader reader = null;

		if (getLongReadsInputFormat().endsWith("am")){//bam or sam
			if ("-".equals(getLongReadsInput()))
				reader = SamReaderFactory.makeDefault().open(SamInputResource.of(System.in));
			else
				reader = SamReaderFactory.makeDefault().open(new File(getLongReadsInput()));	
		}else{
			logger.info("Starting alignment by" + getAligner() +" at " + new Date());
			ProcessBuilder pb = null;
			List<String> command = new ArrayList<>();
			command.add(getAligner());
			if(getAligner().endsWith("minimap2")) {
				command.add("-a");
				command.addAll(Arrays.asList(getAlignerOpts().split("\\s")));
				command.add("-K20000");
				command.add(getPrefix()+File.separator+"assembly_graph.mmi");
				command.add(getLongReadsInput());
			}
			else if(getAligner().endsWith("bwa")) {
				command.add("mem");
				command.addAll(Arrays.asList(getAlignerOpts().split("\\s")));
				command.add("-K20000");
				command.add(getPrefix()+File.separator+"assembly_graph.fasta");
				command.add(getLongReadsInput());
			}
			
			if ("-".equals(getLongReadsInput())){
				pb = new ProcessBuilder(command).redirectInput(Redirect.INHERIT);
			}else{
				pb = new ProcessBuilder(command);
			}

			alignmentProcess  = pb.redirectError(ProcessBuilder.Redirect.to(new File(getPrefix()+File.separator+"alignment.log"))).start();

			logger.info(getAligner() + " started!");			

			reader = SamReaderFactory.makeDefault().open(SamInputResource.of(alignmentProcess.getInputStream()));

		}
		SAMRecordIterator iter = reader.iterator();

		String readID = "";
		Sequence read = null;
		ArrayList<Alignment> hits =  new ArrayList<Alignment>();// alignment record of the same read;	
		SAMRecord curRecord=null;
		
		Thread thread = new Thread(observer);
		thread.start();	
		
		while (iter.hasNext()) {
			if(getStopSignal())
				break;
			
			try {
				curRecord = iter.next();
			}catch(Exception e) {
				logger.error("Error SAM record: \n {}", e);
				break;
			}
			
			if (curRecord.getReadUnmappedFlag() || curRecord.getMappingQuality() < Alignment.MIN_QUAL){		
				logger.debug("Ignore one unmapped or low-quality map record!");
				if (!readID.equals(curRecord.getReadName())){
					update(read, hits);
					hits = new ArrayList<Alignment>();
					read = GraphUtil.getQueryReadFromSAMRecord(curRecord);
					readID = curRecord.getReadName();
				}
				continue;		
			}
			
			String refName = curRecord.getReferenceName();
			String refID = refName.split("_").length > 1 ? refName.split("_")[1]:refName;
			
			//check if this node still in. FIXME: do not remove nodes for metagenomics' graph?
			if (simGraph.getNode(refID)==null) {
				logger.debug("Ignore record with reference" + refID + "not found (removed) from the graph!");
				if (!readID.equals(curRecord.getReadName())){
					update(read, hits);
					hits = new ArrayList<Alignment>();
					read = GraphUtil.getQueryReadFromSAMRecord(curRecord);
					readID = curRecord.getReadName();
				}
				continue;
			}
			Alignment curAlignment = new Alignment(curRecord, (BDNode) simGraph.getNode(refID)); 

			//////////////////////////////////////////////////////////////////
			
			if (!readID.equals("") && !readID.equals(curRecord.getReadName())) {	
				update(read, hits);
				hits = new ArrayList<Alignment>();
				read = GraphUtil.getQueryReadFromSAMRecord(curRecord);
				readID = curRecord.getReadName();

			}	
			hits.add(curAlignment); 
		}// while
		observer.stopWaiting();
		thread.join();
		iter.close();
		reader.close();

		terminateAlignmentProcess();	

	}
	
	/**
	 * Version 2 using PAF format instead of SAM/BAM
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void assembly2() 
			throws IOException, InterruptedException{
		int timeInterval=(int) (Math.round(Math.log10(simGraph.getNodeCount()))-1); //estimated interval time based on graph complexity
		timeInterval=(timeInterval>1?timeInterval:1)*10;
		int readInterval=100;
		
		observer.setReadPeriod(RealtimeGraphWatcher.R_INTERVAL!=0?RealtimeGraphWatcher.R_INTERVAL:readInterval);
		observer.setTimePeriod((RealtimeGraphWatcher.T_INTERVAL!=0?RealtimeGraphWatcher.T_INTERVAL:timeInterval) * 1000);

		if(getLongReadsInput().isEmpty()) {
			logger.info("Scaffolding is ignored due to lack of long-read input!");
			return;
		}else
			logger.info("Scaffolding ready at " + new Date());


		InputStreamReader inputStream = null;

		if (getLongReadsInputFormat().endsWith("paf")){//bam or sam
			if ("-".equals(getLongReadsInput()))
				inputStream = new InputStreamReader(System.in);
			else
				inputStream = new FileReader(getLongReadsInput());	
		}else if(getLongReadsInputFormat().contains("fast")){
			logger.info("Starting alignment by: " + getAligner() + " " + getAlignerOpts() + " at " + new Date());
			ProcessBuilder pb = null;
			List<String> command = new ArrayList<>();
			command.add(getAligner());
//			command.add("-a");
			command.addAll(Arrays.asList(getAlignerOpts().split("\\s")));
			command.add("-K20000");
			command.add(getPrefix()+File.separator+"assembly_graph.mmi");
			command.add(getLongReadsInput());
			

			
			if ("-".equals(getLongReadsInput())){
				pb = new ProcessBuilder(command).redirectInput(Redirect.INHERIT);
			}else{
				pb = new ProcessBuilder(command);
			}

			alignmentProcess  = pb.redirectError(ProcessBuilder.Redirect.to(new File(getPrefix()+File.separator+"alignment.log"))).start();

			logger.info(getAligner() + " started!");			

			inputStream = new InputStreamReader(alignmentProcess.getInputStream());

		}
		
		try(BufferedReader reader=new BufferedReader(inputStream)){
			String readID = "";
			Sequence read = null;
			ArrayList<Alignment> hits =  new ArrayList<Alignment>();// alignment record of the same read;	
			PAFRecord curRecord=null;
			
			Thread thread = new Thread(observer);
			thread.start();	
			String line;
			while ((line=reader.readLine()) != null) {
				if(getStopSignal())
					break;
				
				try {
					curRecord = new PAFRecord(line);
				}catch(Exception e) {
					logger.error("Error reading PAF record: \n {}", e);
	//				continue;
					break;
				}
				
				if (curRecord.qual < Alignment.MIN_QUAL){		
					logger.debug("Ignore low-quality map record!");
					if (!readID.equals(curRecord.qname)){
						update(read, hits);
						hits = new ArrayList<Alignment>();
						readID = curRecord.qname;
						read = GraphUtil.getNSequence(curRecord.qname, curRecord.qlen);//there is no read data from PAF, so just fake one!

					}
					continue;		
				}
				
				String refName = curRecord.tname;
				String refID = refName.split("_").length > 1 ? refName.split("_")[1]:refName;
				
				//check if this node still in. FIXME: do not remove nodes for metagenomics' graph?
				if (simGraph.getNode(refID)==null) {
					logger.debug("Ignore record with reference " +refID+" not found (removed) from the graph!");
					if (!readID.equals(curRecord.qname)){
						update(read, hits);
						hits = new ArrayList<Alignment>();
						readID = curRecord.qname;
						read = GraphUtil.getNSequence(curRecord.qname, curRecord.qlen);//there is no read data from PAF, so just fake one!

					}
					continue;
				}
				Alignment curAlignment = new Alignment(curRecord, (BDNode) simGraph.getNode(refID)); 
	
				//////////////////////////////////////////////////////////////////
				
				if (!readID.equals("") && !readID.equals(curRecord.qname)) {	
					update(read, hits);
					hits = new ArrayList<Alignment>();
					readID = curRecord.qname;
					read = GraphUtil.getNSequence(curRecord.qname, curRecord.qlen);//there is no read data from PAF, so just fake one!

				}	
				hits.add(curAlignment); 
			}// while
			
			observer.stopWaiting();
			thread.join();
			terminateAlignmentProcess();	
		}

	}
	
	//update when more read alignments coming in
	synchronized void update(Sequence nnpRead, ArrayList<Alignment> alignments){
		if(alignments.isEmpty() || nnpRead==null)
			return;
		
		currentReadCount ++;
		currentBaseCount += nnpRead.length();

		List<BDPath> paths=simGraph.uniqueBridgesFinding(nnpRead, alignments);
		if(paths!=null)
		    paths.stream().forEach(p->simGraph.reduceUniquePath(p));
	} 		
	
	public void terminateAlignmentProcess() {
 		if (alignmentProcess != null){
 			alignmentProcess.destroy();
 		}		
 	}
	
	
	@Deprecated
	//last attempt to connect bridges greedily. Now move to RealtimeGraphWatcher
	public void postProcessGraph() throws IOException{
		System.out.printf("Post-processing the graph by greedy path-finding algorithm. Please wait...\n");
		HashSet<GoInBetweenBridge> 		unsolved=simGraph.getUnsolvedBridges(),
										solved=new HashSet<>();
		while(true){
			boolean changed=false;
			for(GoInBetweenBridge brg:unsolved){
				logger.debug("Last attempt on incomplete bridge" + brg.getEndingsID() +" : anchors="+brg.getNumberOfAnchors() + "\n" + brg.getAllPossiblePaths());
				//Take the current best path among the candidate of a bridge and connect the bridge(greedy)
				if(brg.getCompletionLevel()>=3){ 
					simGraph.getNewSubPathsToReduce(brg.getBestPath(brg.pBridge.getNode0(),brg.pBridge.getNode1())).stream().forEach(p->simGraph.reduceUniquePath(p));
					solved.add(brg);
					changed=true;
				}else{
					brg.scanForAnEnd(true);	
					changed=brg.steps.connectBridgeSteps(true);
					
					//return appropriate path
					if(changed){
						simGraph.getNewSubPathsToReduce(brg.getBestPath(brg.steps.start.getNode(),brg.steps.end.getNode())).stream().forEach(p->simGraph.reduceUniquePath(p));
						solved.add(brg);
					}
					else
						logger.debug("Last attempt failed!");
				}
	
			}
			if(solved.isEmpty()&&!changed)
				break;
			else{
				unsolved.removeAll(solved);
				solved.clear();
			}
				
		}
        //update for the last time
        observer.update(true);
		System.out.printf("Input stats: read count=%d base count=%d\n", currentReadCount, currentBaseCount);
		
		observer.outputFASTA(getPrefix()+File.separator+"npgraph_assembly.fasta");
		observer.outputJAPSA(getPrefix()+File.separator+"npgraph_assembly.japsa");
		observer.outputAssGFA(getPrefix()+File.separator+"npgraph_assembly.gfa");
		observer.outputOrigGFA(getPrefix()+File.separator+"npgraph_components.gfa");

		
		//delete temporary files
//		File tmpFolder=new File(getPrefix()+File.separator+"npGraph_tmp");
//		if(tmpFolder.exists()){
//		  try {
//			Files.walk(tmpFolder.toPath())
//			    .sorted(Comparator.reverseOrder())
//			    .map(Path::toFile)
//			    .forEach(File::delete);
//			} catch (IOException e) {
//				LOG.info("Cannot remove existed temporary folder {}!", tmpFolder.getPath());
//				e.printStackTrace();
//			}
//  		}
		
	}

    
    @SuppressWarnings("resource")
	public static void promptEnterKey(){
    	   System.out.println("Press \"ENTER\" to continue...");
    	   Scanner scanner = new Scanner(System.in);
    	   scanner.nextLine();
    	}
    
    
    public boolean checkMinimap2() {    		
		ProcessBuilder pb = new ProcessBuilder(getAligner(),"-V").redirectErrorStream(true);
		Process process;
		try {
			process = pb.start();
			BufferedReader bf = SequenceReader.openInputStream(process.getInputStream());
	
	
			String line;
			String version = "";
			Pattern versionPattern = Pattern.compile("^(\\d+\\.\\d+).*");
			Matcher matcher=versionPattern.matcher("");
			
			while ((line = bf.readLine())!=null){				
				matcher.reset(line);
				if (matcher.find()){
				    version = matcher.group(1);
				    break;//while
				}
				
								
			}	
			bf.close();
			
			if (version.length() == 0){
				setCheckLog("Command " + getAligner() + " -V failed!");
				return false;
			}else{
				logger.info("minimap version: " + version);
				if (version.compareTo("2.0") < 0){
					setCheckLog("Require minimap version 2 or above!");
					return false;
				}
			}
		} catch (IOException e) {
			setCheckLog("Error running: " + getAligner() + "\n" + e.getMessage());
			return false;
		}
		
		return true;
			
    }
    
    public boolean checkBWA() {    		
		try{
			ProcessBuilder pb = new ProcessBuilder(getAligner()).redirectErrorStream(true);
			Process process =  pb.start();
			BufferedReader bf = SequenceReader.openInputStream(process.getInputStream());


			String line;
			String version = "";
			Pattern versionPattern = Pattern.compile("^Version:\\s(\\d+\\.\\d+\\.\\d+).*");
			Matcher matcher=versionPattern.matcher("");
			
			while ((line = bf.readLine())!=null){				
				matcher.reset(line);
				if (matcher.find()){
				    version = matcher.group(1);
				    break;//while
				}
				
								
			}	
			bf.close();
			
			if (version.length() == 0){
				setCheckLog("Command " + getAligner() + " doesn't give version info. Check version failed!");
				return false;
			}else{
				logger.info("bwa version: " + version);
				if (version.compareTo("0.7.11") < 0){
					setCheckLog(" Require bwa of 0.7.11 or above");
					return false;
				}
			}

		}catch (IOException e){
			setCheckLog("Error running: " + getAligner() + "\n" + e.getMessage());
			return false;
		}
		
		return true;
			
    }
    
    public boolean checkMSA() {    		
		try{	
			String[] cmd;
			if(getMSA().startsWith("kalign")) //maybe kalign3
				cmd=new String[]{"kalign","-h"}; //important, as kalign acted weird without this
			else
				cmd=new String[]{getMSA()};
			
			ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
			Process process =  pb.start();
			BufferedReader bf = SequenceReader.openInputStream(process.getInputStream());
			String line;
			boolean found=false;
			while ((line = bf.readLine())!=null){				
				if (line.toLowerCase().contains("usage:")){ // kalign, kalign3, poa, spoa all print out "Usage:" if run without parameter
					found=true;
					break;
				}
			}	
			bf.close();
			return found;

		}catch (IOException e){
			setCheckLog("Error running: " + getMSA() + "\n" + e.getMessage());
			return false;
		}
					
    }
	
	public static void main(String[] argv) throws IOException, InterruptedException{
		HybridAssembler hbAss = new HybridAssembler();
		
		hbAss.setShortReadsInput("/home/sonhoanghguyen/Projects/scaffolding/data/spades_3.7/EcK12S-careful/assembly_graph.fastg");
		hbAss.setShortReadsInputFormat("fastg");
		hbAss.prepareShortReadsProcess();
		hbAss.setLongReadsInput("/home/sonhoanghguyen/Projects/scaffolding/data/spades_3.7/EcK12S-careful/assembly_graph.sam");
		hbAss.setLongReadsInputFormat("sam/bam");
		hbAss.prepareLongReadsProcess();
		hbAss.assembly();

	}
	
}
