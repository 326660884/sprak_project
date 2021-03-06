package com.xuihuipeg
import org.apache.spark.SparkConf
import org.apache.spark.sql.{DataFrame, SparkSession}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.{MongoClient, MongoClientURI}

/**
  * Movie数据集，数据集字段通过分割
  *
  * 151^                          电影的ID
  * Rob Roy (1995)^               电影的名称
  * In the highlands ....^        电影的描述
  * 139 minutes^                  电影的时长
  * August 26, 1997^              电影的发行日期
  * 1995^                         电影的拍摄日期
  * English ^                     电影的语言
  * Action|Drama|Romance|War ^    电影的类型
  * Liam Neeson|Jessica Lange...  电影的演员
  * Michael Caton-Jones           电影的导演
  *
  * tag1|tag2|tag3|....           电影的Tag
  **/

case class Movie(val mid: Int, val name: String, val descri: String, val timelong: String, val issue: String,
                 val shoot: String, val language: String, val genres: String, val actors: String, val directors: String)

/**
  * Rating数据集，用户对于电影的评分数据集，用，分割
  *
  * 1,           用户的ID
  * 31,          电影的ID
  * 2.5,         用户对于电影的评分
  * 1260759144   用户对于电影评分的时间
  */
case class Rating(val uid: Int, val mid: Int, val score: Double, val timestamp: Int)

/**
  * Tag数据集，用户对于电影的标签数据集，用，分割
  *
  * 15,          用户的ID
  * 1955,        电影的ID
  * dentist,     标签的具体内容
  * 1193435061   用户对于电影打标签的时间
  */
case class Tag(val uid: Int, val mid: Int, val tag: String, val timestamp: Int)

/**
  * MongoDB的连接配置
  * @param uri   MongoDB的连接
  * @param db    MongoDB要操作数据库
  */
case class MongoConfig(val uri:String, val db:String)

object DataLoader {

  val MOVIE_DATA_PATH: String = "E:\\sprakproject\\MovieRecommendSystem\\recommender\\dataloader\\src\\main\\resources\\movies.csv"
  val RATING_DATA_PATH = "E:\\sprakproject\\MovieRecommendSystem\\recommender\\dataloader\\src\\main\\resources\\ratings.csv"
  val TAG_DATA_PATH     = "E:\\sprakproject\\MovieRecommendSystem\\recommender\\dataloader\\src\\main\\resources\\tags.csv"

  val MONGODB_MOVIE_COLLECTION = "Movie"
  val MONGODB_RATING_COLLECTION = "Rating"
  val MONGODB_TAG_COLLECTION = "Tag"

  def main(args: Array[String]): Unit = {
    //recommender -> 推荐系统
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://192.168.10.37/recommender",
      "mongo.db" -> "recoummender",
      "ec.httpHosts" -> "",
      "es.httpHosts" -> "",
      "es.transportHosts" -> "",
      "es.index" -> "",
      "es.cluster.name" -> ""
    )

    //1.创建一个SparkConf配置
    val sparkConf = new SparkConf().setAppName("DataLoader").setMaster(config.get("spark.cores").get)
    //2.创建SparkSeesion
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    //3.将数据加载进去
    import spark.implicits._

    val movieRDD = spark.sparkContext.textFile(MOVIE_DATA_PATH)
    val movieDF = movieRDD.map(item => {
      val attr = item.split("\\^")
      Movie(attr(0).toInt, attr(1).trim, attr(2).trim, attr(3).trim, attr(4).trim, attr(5).trim, attr(6).trim, attr(7).trim, attr(8).trim, attr(9).trim)
    }).toDF()

    val ratingRDD = spark.sparkContext.textFile(RATING_DATA_PATH)

    val ratingDF = ratingRDD.map( item =>{
      val attr=item.split(",")
      Rating(attr(0).toInt,attr(1).toInt,attr(2).toDouble,attr(3).toInt)
    }).toDF


    val tagRDD = spark.sparkContext.textFile(TAG_DATA_PATH)

    val tagDF=tagRDD.map(item =>{
      val attr = item.split(",")
      Tag(attr(0).toInt,attr(1).toInt,attr(2).trim,attr(3).toInt)
    }).toDF
    //4.将数据保存到MongoDB中
    implicit val mongoConfig = MongoConfig(config.get("mongo.uri").get,config.get("mongo.db").get)
    storeDataInMongoDB(movieDF, ratingDF, tagDF)
    //5.关闭
    spark.stop()
  }

  // 将数据保存到MongoDB中的方法
  def storeDataInMongoDB(movieDF: DataFrame, ratingDF:DataFrame, tagDF:DataFrame)(implicit mongoConfig: MongoConfig): Unit = {

    //新建一个到MongoDB的连接
    val mongoClient = MongoClient(MongoClientURI(mongoConfig.uri))
    //如果MongoDB中有对应的数据库，那么应该删除
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).dropCollection()
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).dropCollection()

    //将当前数据写入到MongoDB
    movieDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    ratingDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    tagDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_TAG_COLLECTION)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //对数据表建索引
    mongoClient(mongoConfig.db)(MONGODB_MOVIE_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("uid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_RATING_COLLECTION).createIndex(MongoDBObject("mid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("uid" -> 1))
    mongoClient(mongoConfig.db)(MONGODB_TAG_COLLECTION).createIndex(MongoDBObject("mid" -> 1))

    //关闭MongoDB的连接
    mongoClient.close()
  }

}
