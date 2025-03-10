# MeshTest: End-to-End Testing for Service Mesh Traffic Management

## Setup environment
```
make env
```

## Generate end-to-end test cases
```
run model/src/main/java/ConfGen
```

## Check generated test cases
```
run model/src/main/java/Controller from base directory
```

## Cite our paper
```
@inproceedings{zhengmeshtest,
  title={MeshTest: End-to-End Testing for Service Mesh Traffic Management},
  author={Zheng, Naiqian and Qiao, Tianshuo and Liu, Xuanzhe and Jin, Xin},
  booktitle={22st USENIX Symposium on Networked Systems Design and Implementation (NSDI 25)},
  year={2025}
}
```