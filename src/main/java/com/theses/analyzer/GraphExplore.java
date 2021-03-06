package com.theses.analyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.*;

import edu.uci.ics.jung.algorithms.scoring.HITS;

import com.jsoniter.*;
import com.jsoniter.any.Any;
import com.jsoniter.output.JsonStream;

public class GraphExplore {
	
	double MAX_POINT = .50;
	int EVENT_ISSUES = 0, EVENT_PR = 1, ALL = 0, SCORES_ONLY = 1, COUNTER_ONLY = 2, PR_ONLY = 3, ISSUES_ONLY = 4, FINAL_HUB = -1, FINAL_WEIGHTED = -2;
	Graph graph = new SingleGraph("Collaboration Graph");
	List<GitNode> gitList = new ArrayList<GitNode>();
	Map<String, Double> authorityScores = new HashMap<String, Double>();
	Map<String, Double> hubScores = new HashMap<String, Double>();
	Map<String, Double> weightedScores = new HashMap<String, Double>();
	double norm_fb , norm_ang , norm_ang2 = 0.0 , total ;
	GraphExplore() {
		graph.setStrict( false );
		graph.setAutoCreate( true );
		//parseAndGraph( loadFromFile("C:/data/sample_1.json"), EVENT_ISSUES);
		//parseAndGraph( loadFromFile("/Users/rrosal/Documents/sample_1.json"), EVENT_ISSUES);
		
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/dec_is.json"), EVENT_ISSUES);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/jan_is.json"), EVENT_ISSUES);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/feb_is.json"), EVENT_ISSUES);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/mar_is.json"), EVENT_ISSUES);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/apr_is.json"), EVENT_ISSUES);
//		
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/dec_pr.json"), EVENT_PR);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/jan_pr.json"), EVENT_PR);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/feb_pr.json"), EVENT_PR);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/mar_pr.json"), EVENT_PR);
		parseAndGraph( loadFromFile("/Users/rrosal/Documents/df/apr_pr.json"), EVENT_PR);

		
		setGraph();
		initNormalizers();
//		graph.display();
		calculateScores();
		calculateWeightedScores();
		getStats();
		
		// optional prints true if score before weighted
		getTopRepositories( true, false );
		getTopRepositories( false , false ); 
		//printResult(ALL); // for debug, print after weighted along with other data.
	}
	
	double pr_ctr = 0.0, is_ctr = 0.0;
	public void calculateWeightedScores(){
		for( GitNode n : gitList ) {
			double open = 0.0 , close = 0.0 , merged = 0.0, pr = 0.0;
			if( !n.isUserNode()) {
				for( GitNode ng : n.getNeighbors()) {
					Edge edge = graph.getEdge(""+ng.getName()+n.getName());
					String status = edge.getAttribute("status").toString();
					if (status.equals("opened")){
						open++;
						is_ctr++;
					} else if( status.equals("closed")) {
						close++;
						is_ctr++;
					} else if( status.equals("merged")) {
						merged++;
						pr_ctr++;
					} else {
						pr++;
						pr_ctr++;
					}
				}
				double hubScore = hubScores.get(n.getName());
				double boostIssues = MAX_POINT  * ( (open + close) / is_ctr ); 
				double boostPR = MAX_POINT * ( (merged + pr) / pr_ctr );
				n.setEr( (open + close) / (merged + pr)); // set er
				n.setOc( open / close);
				n.setMs( merged / (pr_ctr - merged) );
				double totalBoost = (Double.isNaN(boostIssues)? 0 : boostIssues) + (Double.isNaN(boostPR)? 0 : boostPR);
				n.setBoost( totalBoost );
				n.setStatusCount(open, close);
				n.setPRCount( merged, pr );
				n.setWeightedScore(hubScore + (totalBoost * hubScore));
				weightedScores.put(n.getName(), hubScore + (totalBoost * hubScore));
			}
		}
	}
	
	public void calculateScores(){
		for( int i = 0; i < 10; i++ ) {
			double norm = 0;
			for( GitNode g : gitList ) {
				if( g.isUserNode() ) {
					authorityScores.replace(g.getName(), 0.0);
					for( GitNode n : g.getNeighbors() ) {
						double newAuth = authorityScores.get(g.getName()) + hubScores.get(n.getName());
						
						authorityScores.replace(g.getName(), newAuth );
					}
					norm = norm + ( authorityScores.get(g.getName()) * authorityScores.get(g.getName()) );
				}
			}
			norm = Math.sqrt(norm);
			for( GitNode g : gitList ) {
				if ( g.isUserNode() ){
					double newAuth = authorityScores.get(g.getName()) / norm;
					authorityScores.replace(g.getName(), newAuth);
				}
			}
			norm = 0;
			for( GitNode g : gitList ) {
				if ( !g.isUserNode() ) {
					hubScores.replace(g.getName(), 0.0);
					for( GitNode n : g.getNeighbors() ){
						double newHub = hubScores.get(n.getName()) + authorityScores.get(n.getName());
						hubScores.replace(g.getName(), newHub);
					}
					norm = norm + ( hubScores.get(g.getName()) * hubScores.get(g.getName()));
				}
			}
			norm = Math.sqrt(norm);
			for( GitNode g : gitList ){
				if ( !g.isUserNode() ) {
					double newHub = hubScores.get(g.getName()) / norm;
					hubScores.replace(g.getName(), newHub);
				}
			}	
		}
		
		for( GitNode g : gitList ){
			if ( !g.isUserNode() ) {
				double newHub = hubScores.get(g.getName()) * getNormalize(g.getName());
				hubScores.replace(g.getName(), newHub );
			}
		}	
	}
	
	public double getNormalize( String name ) {
		if( name.equals("facebook/react")){
			return norm_fb;
		} else if (name.equals("angular/angular")){
			return norm_ang;
		} else {
			return norm_ang2;
		}
	}
	
	public void initNormalizers(){
		total = (double) graph.getEdgeCount();
		norm_fb = (double) graph.getNode("facebook/react").getDegree() / total ;
		norm_ang = (double) graph.getNode("angular/angular").getDegree() / total ;
		norm_ang2 = (double) graph.getNode("angular/angular.js").getDegree() / total;
	}
	
	public void setGraph() {
		for( Node n : graph ){
			GitNode gn = new GitNode(n);
			Iterator<Node> it = n.getNeighborNodeIterator();
			
			while( it.hasNext()) {
				Node gnb = it.next();
				GitNode neig = new GitNode(gnb);
				if( gnb.getAttribute("ui.label").toString().contains("/")){
					neig.notUserNode();
				}
				gn.addNeighbor(neig);	
			}
			
			if( n.getAttribute("ui.label").toString().contains("/")){
				gn.notUserNode();
			}
			
			authorityScores.put(gn.getName(),  1.0);
			hubScores.put(gn.getName(), 1.0);
			gitList.add(gn);
		}
	}
	
	public byte[] loadFromFile(String addr) {
		File file = new File(addr);
		byte[] bytesArray;
		try{
			bytesArray = new byte[ (int) file.length()];
			FileInputStream fis = new FileInputStream(file);
			fis.read(bytesArray);
			fis.close();
			return bytesArray;
		} catch( Exception e) {
			bytesArray = null;
			e.printStackTrace();
		} 
		
		return bytesArray;
	}
	
	public void parseAndGraph( byte[] bytesArray , int eventType ){
		
		Any obj = JsonIterator.deserialize(bytesArray);
		Node usr, repo;
		String payload, issueObj, userObj, status, repo_name, username;
		if( eventType == 0) {		
			for( Any ob : obj ) {
				payload = ob.toString("payload"); // get payload object from response object
				issueObj = JsonIterator.deserialize(payload).toString("issue"); // get issue object from payload object
				userObj = JsonIterator.deserialize(issueObj).toString("user"); // get user object from issue object
				
				repo_name = ob.toString("repo_name"); // get repo name
				username = JsonIterator.deserialize(userObj).toString("login"); // get username of issue sender 
				status = JsonIterator.deserialize(payload).toString("action"); // get issue status (open or closed)
				
				usr = graph.addNode(username);
				usr.addAttribute("ui.label", username);
				usr.addAttribute("ui.style", "text-offset: -5, -10;");
//				usr.addAttribute("ui.style", "text-background-mode: plain;");
				repo = graph.addNode(repo_name);
				repo.addAttribute("ui.label", repo_name);
				
//				Thread.sleep(100);
				Edge edge = graph.addEdge( ""+usr.getId()+repo.getId() , usr, repo);
				edge.addAttribute("status", status);
				edge.addAttribute("ui.label", status);
			}
		} else if ( eventType == 1) { // parsing for pull request
			for( Any ob : obj ) {
				payload = ob.toString("payload"); // get payload object from response object
				issueObj = JsonIterator.deserialize(payload).toString("pull_request"); // get pull request object from payload object
				userObj = JsonIterator.deserialize(issueObj).toString("user"); // get user object from issue object
				
				repo_name = ob.toString("repo_name"); // get repo name
				username = JsonIterator.deserialize(userObj).toString("login"); // get username of pull request sender 
				status = JsonIterator.deserialize(issueObj).toString("merged"); // get issue status (open or closed)
				
				usr = graph.addNode(username);
				usr.addAttribute("ui.label", username);
				repo = graph.addNode(repo_name);
				repo.addAttribute("ui.label", repo_name);
//				Thread.sleep(100);
				Edge edge = graph.addEdge( ""+usr.getId()+repo.getId() , usr, repo);
				edge.addAttribute("status",(status == "true" ? "merged" : "unmerged"));
				edge.addAttribute("ui.label", (status == "true" ? "merged" : "unmerged"));
			}
		} else {
			System.out.println("Unhandled parsing event type");
		}
		
		for( Node g : graph ) {
			if( g.getId().equals("facebook/react")){ 
				g.addAttribute("ui.style", "fill-color: blue;");
			} else if ( g.getId().equals("angular/angular")) {
				g.addAttribute("ui.style", "fill-color: orange;");
			} else if( g.getId().equals("angular/angular.js")){
				g.addAttribute("ui.style", "fill-color: yellow;");
			}
		}
	}
	
	// sort top 10
	Map<String, Double> sorted = new HashMap<String, Double>();
	public void getTopRepositories( boolean beforeWeighted, boolean writeToFile ){
		String preText;
		if( beforeWeighted ){	
			sorted = SortMap(hubScores);
			preText = "hubTop10@" + new Date().toString();
			System.out.println("Hub scores:");
		} else {
			sorted = (weightedScores);
			preText = "weightedTop10@" + new Date().toString();
			System.out.println("Weighted scores:");
		}
		
		int ctr = 0;
		Map<String, Double> tmp = new HashMap<String, Double>();
		for (Map.Entry<String, Double> entry : sorted.entrySet()) {	
			if( entry.getKey().contains("/") && ctr < 10) {
				tmp.put(entry.getKey(), entry.getValue());
				System.out.println(entry.getKey() + " -> " + entry.getValue() );
				ctr++;
			} 
		}
		
		if( writeToFile ) {
			try {
				FileWriter file = new FileWriter("/Users/rrosal/Documents/" + preText + ".json");
				file.write(JsonStream.serialize(tmp));
				file.flush();
				file.close();
				System.out.println("File created");
			} catch (Exception e) {
				e.printStackTrace();
			} 
		}
		
	}
	
	public void getStats(){
		System.out.println("Stats");
		for( GitNode g : gitList ){
			if( !g.isUserNode() ) {
				
				System.out.println(g.getName() + ": ER -> " + g.getEr() + " | O/C ->  " + g.getOc() + " | M/S -> " + g.getMs());
			}
		}
	}
	
	public static <K, V extends Comparable<? super V>> Map<K, V> SortMap(final Map<K, V> mapToSort) {
		List<Map.Entry<K, V>> entries = new ArrayList<Map.Entry<K, V>>(mapToSort.size());
 
		entries.addAll(mapToSort.entrySet());
 
		// Sorts the specified list according to the order induced by the specified comparator
		Collections.sort(entries, new Comparator<Map.Entry<K, V>>() {
			public int compare(final Map.Entry<K, V> entry1, final Map.Entry<K, V> entry2) {
				// Compares this object with the specified object for order
				return entry1.getValue().compareTo(entry2.getValue());
			}
		});
 
		Map<K, V> sortedMap = new LinkedHashMap<K, V>();
 
		// The Map.entrySet method returns a collection-view of the map
		for (Map.Entry<K, V> entry : entries) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
 
		return sortedMap;
	}
	
	// not yet finished
	// no pr scores
	public void printResult(int type){
		for( GitNode n : gitList ){
			if( !n.isUserNode()) {
				if( type == ALL ){
					System.out.println( n.getName() 
							+ " -> Hub: " + hubScores.get(n.getName()) + " | Auth: " + authorityScores.get(n.getName())
							+ " | " + "Open: " + n.getOpenCount() + " | Close: " + n.getCloseCount()
							+ " | Boost: " + n.getBoost()
							+ " | Merged: " + n.getMerged() 
							+ " | Total PR: " + n.getPr()
							+ " | weighted score: " + n.getWeightedScore());
				} else if( type == COUNTER_ONLY) {
					System.out.println( n.getName() 
							+" -> Open: " + n.getOpenCount() + " | Close: " + n.getCloseCount()
							+" | Merged: " + n.getMerged() + " | Total PR: " + n.getPr() );
				} else if( type == SCORES_ONLY ){
					System.out.println( n.getName() 
							+ " -> Hub: " + hubScores.get(n.getName()) + " | Auth: " + authorityScores.get(n.getName())
							+ " | Boost: " + n.getBoost()
							+ " | weighted score: " + n.getWeightedScore());
				} else if( type == PR_ONLY ){
					System.out.println( n.getName() 
							+" | Merged: " + n.getMerged() + " | Total PR: " + n.getPr() );
				} else if( type == ISSUES_ONLY ){
					System.out.println( n.getName() 
							+" -> Open: " + n.getOpenCount() + " | Close: " + n.getCloseCount());
				} else if( type == FINAL_HUB ) {	
				}
				else {
					System.out.println("Unhandled print type, fallback print all!");
					printResult(ALL);
				}
			}	
		}
	}
	
}
