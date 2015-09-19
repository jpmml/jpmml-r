print("R version:")
print(paste(R.version$major, R.version$minor, sep = "."))

print("R package versions:")
packages = c("caret", "gbm", "party", "randomForest", "rattle")
for(package in packages){
	print(paste(package, packageVersion(package)))
}

# update.packages()