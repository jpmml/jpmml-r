print("R version:")
print(paste(R.version$major, R.version$minor, sep = "."))

latest_versions = available.packages()

print("R package versions:")
packages = c("ada", "caret", "e1071", "earth", "elmNN", "gbm", "IsolationForest", "nnet", "party", "pls", "randomForest", "ranger", "rattle", "rms", "rpart", "xgboost")
for(package in packages){
	version = packageVersion(package)
	if(!(package %in% c("IsolationForest"))){
		latest_version = latest_versions[package, "Version"]
	} else {
		latest_version = "NA"
	}

	print(paste(package, version, latest_version))
}

# update.packages()
