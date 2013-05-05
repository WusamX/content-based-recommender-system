package ch.epfl.advdb.milestone2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.filecache.DistributedCache;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class DataClustering {
	
	Configuration configuration;
	
	String inputDir, outputDir;
	int numOfIterations, numOfReducers, numOfMappers, numOfIMDBClusters, numOfNetflixClusters;
	
	public DataClustering(String inputDir, String outputDir,
						  int numOfIterations, int numOfReducers,
						  int numOfMappers, int numOfIMDBClusters,
						  int numOfNetflixClusters) {
		
		this.inputDir = inputDir;
		this.outputDir = outputDir;
		this.numOfIterations = numOfIterations;
		this.numOfReducers = numOfReducers;
		this.numOfMappers = numOfMappers;
		this.numOfIMDBClusters = numOfIMDBClusters;
		this.numOfNetflixClusters = numOfNetflixClusters;
		
		configuration = new Configuration();
		configuration.set("mapred.textoutputformat.separator",
						  String.valueOf(Constants.TEXT_SEPARATOR));
		
		configuration.setInt("mapred.map.tasks", numOfMappers);
	}
	
	public void createInitialCentroids() throws IOException {
		IMDBCentroidBuilder icb = new IMDBCentroidBuilder(configuration, inputDir+"/features/", outputDir+"/imdb_centroids_0/centroids.dat", numOfIMDBClusters);
		icb.createInitialCentroids();
		
		NetflixCentroidBuilder ncb = new NetflixCentroidBuilder(configuration, inputDir+"/V/", outputDir+"/netflix_centroids_0/centroids.dat", numOfNetflixClusters);
		ncb.createInitialCentroids();
	}
	
	private String getName(String str, int iteration) {
		return str+"_"+iteration;
	}

	
	@SuppressWarnings("deprecation")
	public void execute() 
			throws IOException, InterruptedException, ClassNotFoundException {
		
		createInitialCentroids();
		
		for(int iter = 0; iter < 1; iter++) {
			System.out.println("Run iteration: "+iter);
			
			{
				Configuration conf = new Configuration(configuration);
				conf.setInt("mapred.reduce.tasks", numOfIMDBClusters);
				
				FileSystem fs = FileSystem.get(conf);
				
				FileStatus[] fileStatus = fs.listStatus(new Path(outputDir+"/imdb_centroids_"+iter+"/"));
				for (FileStatus status : fileStatus) {
				    DistributedCache.addCacheFile(status.getPath().toUri(), conf);
				}
				
				DistributedCache.createSymlink(conf);
				
				DataClusteringJob imdbClustering = new DataClusteringJob(
						conf, getName("IMDB Clustering", iter), "I",
						ClusterMapper.class, ClusterReducer.class,
						outputDir+"/imdb_centroids_"+iter+"/", 
						inputDir+"/features/",
						outputDir+"/imdb_centroids_"+(iter+1)+"/");
				
				
				imdbClustering.waitForCompletion(true);
			}
			
//			{
//				Configuration conf = new Configuration(configuration);
//				conf.setInt("mapred.reduce.tasks", numOfVClusters);
//				DistributedCache.addCacheFile(new Path(getName(outputDir+"/netflix_centroids_0/", iter)).toUri(), conf);
//				
//				DataClusteringJob netflixClustering = new DataClusteringJob(conf,
//					getName("Netflix Clustering", iter),
//					ClusterMapper.class, ClusterReducer.class,
//					getName(outputDir+"/netflix", iter), inputDir+"/netflix/",
//					getName(outputDir+"/netflix", iter+1));
//				
//				netflixClustering.waitForCompletion(true);
//			}
			
		}	
	}
	
	private class DataClusteringJob extends Job {
		
		@SuppressWarnings("deprecation")
		public DataClusteringJob(Configuration conf, String jobName, String matrixType,
				Class<? extends Mapper<LongWritable, Text, 
						IntWritable, FeatureWritable>> mapper,
				Class<? extends Reducer<IntWritable, FeatureWritable, 
						Text, FeatureWritable>> reducer,
						String distributedCacheFilePath, String inputPath, 
						String outputPath) throws IOException {
			
			super(conf, jobName);
			
			setJarByClass(DataClusteringJob.class);
			
			setMapperClass(mapper);
			setReducerClass(reducer);
			
			conf.setStrings(Constants.MATRIX_TYPE, matrixType);

			setOutputKeyClass(CentroidWritable.class);
			setOutputValueClass(ClusterMoviesWritable.class);
			setMapOutputKeyClass(IntWritable.class);
			setMapOutputValueClass(FeatureWritable.class);
			
			setNumReduceTasks(Constants.NUM_OF_REDUCERS);

			setInputFormatClass(TextInputFormat.class);
			setOutputFormatClass(TextOutputFormat.class);

			FileInputFormat.addInputPath(this, new Path(inputPath));
			FileOutputFormat.setOutputPath(this, new Path(outputPath));
			
		}
		
	}
}


