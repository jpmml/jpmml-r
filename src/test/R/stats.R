source("util.R")

iris = loadIrisCsv("Iris")

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

	storeRds(iris.kmeans, "KMeansIris")
	storeCsv(data.frame("cluster" = cluster, "affinity(1)" = affinity(iris_x, center_1), "affinity(2)" = affinity(iris_x, center_2), "affinity(3)" = affinity(iris_x, center_3), check.names = FALSE), "KMeansIris")
}

set.seed(42)

generateKMeansIris()