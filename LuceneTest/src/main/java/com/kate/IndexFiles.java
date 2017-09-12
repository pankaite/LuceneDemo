package com.kate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class IndexFiles {
	
	private IndexFiles(){}

	public static void main(String[] args) {
		
		String indexPath = "index"; //索引存放的路径
		String docsPath = "src/main/resources/com/kate"; //需要建立索引的文档路径
		boolean create = true; //是否重新创建索引
		
		//判断文档路径是否可读
		final Path docDir = Paths.get(docsPath);
		final Path indexDir = Paths.get(indexPath);
		if(!Files.isReadable(docDir)){
			System.out.println("文件目录" + docDir.toAbsolutePath() + "不存在或不可读，请重新确认！");
			System.exit(1);
		}
		
		System.out.println("开始为" +docDir.toAbsolutePath() + "里的文档建立索引到" + indexDir.toAbsolutePath() + "路径下");
		Date start = new Date();
		try {
			Directory directory = FSDirectory.open(Paths.get(indexPath)); //索引目录
			Analyzer analyzer = new StandardAnalyzer(); //词法分析器
			IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);//索引写入配置
			
			if(create){
				//移除之前的索引，创建新的索引
				indexWriterConfig.setOpenMode(OpenMode.CREATE);
			}else {
				//在已存在的索引中继续添加
				indexWriterConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
			}
			
			//提供创建索引的性能，如果这么做的话，需要增加JVM的堆大小（-Xmx512m 或 -Xmx1g）
			indexWriterConfig.setRAMBufferSizeMB(256.0);
			
			//通过索引存放目录，及索引写入配置生成索引写入
			IndexWriter indexWriter = new IndexWriter(directory, indexWriterConfig);
			
			//为文档所在目录下的文档创建索引
			indexDocs(indexWriter, docDir);
			
			//如果想最大化搜索性能，可以这么做，但这样的操作代价是很大的，所以只有在索引是相对静态的情况下才是值得的，比如索引已经创建完成
			indexWriter.forceMerge(1);
			
			indexWriter.close();
			
		} catch (IOException e) {
			System.out.println("捕获异常: " + e.getClass() + " " + e.getMessage());
		}
				
		Date end = new Date();
		System.out.println("总共花费: " + (end.getTime() - start.getTime()) + " 毫秒");		
	}

	public static void indexDocs(final IndexWriter indexWriter, Path path) throws IOException {
		//如果是目录，则遍历目录下的所有文件进行创建索引
		if (Files.isDirectory(path)) {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					try {
						indexDoc(indexWriter, file, attrs.lastModifiedTime().toMillis());						
					} catch (IOException ignore) {						
					}
					return FileVisitResult.CONTINUE;
				}

			});
		}
		else {
			indexDoc(indexWriter, path, Files.getLastModifiedTime(path).toMillis());
		}
	}

	public static void indexDoc(IndexWriter indexWriter, Path file, long lastModified) throws IOException {
		InputStream is = Files.newInputStream(file);
		//创建新的空的文档
		Document document = new Document();
		//添加域，比如文件路径，文件修改时间，文件的内容
		document.add(new StringField("path", file.toString(), Field.Store.YES));
		document.add(new LongPoint("modified", lastModified));
		document.add(new TextField("contents", new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))));
		
		if(indexWriter.getConfig().getOpenMode() == OpenMode.CREATE){
			System.out.println("添加文件 " + file);
			indexWriter.addDocument(document);
		}
		else {
			System.out.println("更新文件 " + file);
			indexWriter.updateDocument(new Term("path", file.toString()), document);
		}
	}

}
