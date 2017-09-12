package com.kate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

public class SearchFiles {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {
		String index = "index"; // 索引所在路径
		String field = "contents"; // 索引库中要查找的文档域
		String queries = null; //指定文件名，表示从该文件中读取搜索关键字，不为空则不交互
		int repeat = 0;
		boolean raw = false; // true： 显示所找到文档的编号和得分，false： 显示所找到文档的路径
		String queryString = null; // 指定要搜索的关键字，不为空则不交互，
		int hitsPerPage = 3; // 每页显示多少条记录

		IndexReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
		IndexSearcher searcher = new IndexSearcher(indexReader);
		Analyzer analyzer = new StandardAnalyzer();

		BufferedReader br = null;

		if (queries != null) {
			br = Files.newBufferedReader(Paths.get(queries), StandardCharsets.UTF_8);
		} else {
			br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		}

		QueryParser parser = new QueryParser(field, analyzer);
		while (true) {
			//如果指定了搜索关键字，则不进行交互
			if (queries == null && queryString == null) {
				System.out.println("输入要查询的关键字： ");
			}

			String line = queryString != null ? queryString : br.readLine();

			if (line == null || line.length() == -1) {
				break;
			}

			line = line.trim();
			if (line.length() == 0) {
				break;
			}

			Query query = parser.parse(line);
			System.out.println("开始查找： " + query.toString(field));

			if (repeat > 0) {
				Date start = new Date();
				for (int i = 0; i < repeat; i++) {
					searcher.search(query, 100);
				}
				Date end = new Date();
				System.out.println("花费: " + (end.getTime() - start.getTime()) + "ms");
			}

			doPagingSearch(br, searcher, query, hitsPerPage, raw, queries == null && queryString == null);

			if (queryString != null) {
				break;
			}		
		}
		indexReader.close();

	}

	public static void doPagingSearch(BufferedReader br, IndexSearcher searcher, Query query, int hitsPerPage,
			boolean raw, boolean interactive) throws IOException {
		//第一次只查找5页的结果，如果需要查看超过5页范围的结果，则需要重新执行查询，然后就能得到所有的结果
		TopDocs results = searcher.search(query, 5 * hitsPerPage);
		ScoreDoc[] hits = results.scoreDocs;
		
		int numTotalHits = results.totalHits;
		System.out.println("总共找到" + numTotalHits + "条匹配的文档");
		
		int start = 0;
		int end = Math.min(numTotalHits, hitsPerPage);
		
		while(true){
			//要查看的结果数超过第一次查找结果的数量
			if(end > hits.length){
				System.out.println("总共查找到" + numTotalHits + "条结果，目前只显示1到" + hits.length + "条");
				System.out.println("是否加载更多?(y/n)");
				String line = br.readLine();
				if(line.length() == 0 || line.charAt(0) == 'n'){
					break;
				}
				//加载全部数据
				hits = searcher.search(query, numTotalHits).scoreDocs;
			}
			
			end = Math.min(hits.length, start+ hitsPerPage);
			
			for(int i = start; i < end; i++){
				if(raw){
					//raw值为true： 显示所找到文档的编号和得分
					System.out.println("文档编号： " + hits[i].doc + " , 匹配得分： " + hits[i].score);
					continue;
				}
				//raw值为false： 显示所找到文档的路径
				Document doc = searcher.doc(hits[i].doc);
				String path = doc.get("path");
				if(path != null){
					System.out.println("第" + (i+1) + "条匹配结果所在文档的路径： " + Paths.get(path).toAbsolutePath());
					//文档没有添加title域，所以不会显示
					String title = doc.get("title");
					if(title != null){
						System.out.println(" , Title: " + title);
					}
				}
				else {
					System.out.println("不存在第" + (i+1) + "条匹配结果所在文档的路径");
				}
			}
			
			//不交互或者没有数据了
			if(!interactive || end == 0){
				break;
			}
			
			if(numTotalHits >= end){
				boolean quit = false;
				while(true){
					System.out.print("按");
					if(start - hitsPerPage >= 0){
						System.out.print("P查看上一页, ");
					}
					if(start + hitsPerPage < numTotalHits){
						System.out.print("N查看下一页, ");
					}
					System.out.println("Q退出或者输入要跳转的页数");
					
					String line = br.readLine();
					if(line.length() == 0 || line.charAt(0) == 'q'){
						quit = true;
						break;
					}
					if(line.charAt(0) == 'p'){
						start = Math.max(0, start - hitsPerPage);
						break;
					}
					else if (line.charAt(0) == 'n') {
						if(start + hitsPerPage < numTotalHits){
							start += hitsPerPage;
						}
						break;
					}
					else {
						int page = Integer.parseInt(line);
						if((page - 1) * hitsPerPage < numTotalHits){
							start = (page - 1) * hitsPerPage;
							break;
						}
						else {
							System.out.println("没有该页");
						}
					}
				}
				if(quit){
					break;
				}
				end = Math.min(numTotalHits, start + hitsPerPage);
			}
		}
	}

}
