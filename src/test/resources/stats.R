source("util.R")

iris = loadIrisCsv("csv/Iris.csv")

iris_x = iris[, -ncol(iris)]

affinity = function(x, center){
	dist = ((x - center)^2)

	return (rowSums(dist))
}

generateKMeansIris = function(){
	iris.kmeans = kmeans(iris_x, centers = 3)

	cluster = fitted(iris.kmeans, method = "classes")

	centers = iris.kmeans$centers

	center_1 = as.list(centers[1, ])
	center_2 = as.list(centers[2, ])
	center_3 = as.list(centers[3, ])

	storeRds(iris.kmeans, "rds/KMeansIris.rds")
	storeCsv(data.frame("cluster" = cluster, "affinity_1" = affinity(iris_x, center_1), "affinity_2" = affinity(iris_x, center_2), "affinity_3" = affinity(iris_x, center_3)), "csv/KMeansIris.csv")
}

set.seed(42)

generateKMeansIris()