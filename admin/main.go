package main

import (
	"context"
	"fmt"
	"github.com/gin-gonic/gin"
	"go.etcd.io/etcd/clientv3"
	"log"
	"net/http"
	"time"
)

func Etcd(cfg clientv3.Config) gin.HandlerFunc {
	etcd, err := clientv3.New(cfg)
	if err != nil {
		log.Printf("Failed to created etcd client: %v\n", err)
		return nil
	}

	return func(c *gin.Context) {
		c.Set("etcd", etcd)
		c.Next()
	}
}

func getDataKey(c *gin.Context) {
	column := c.Params.ByName("column")
	table := c.Params.ByName("table")
	key := fmt.Sprintf("%v/%v", table, column)
	//revision := 0 // TODO: we should keep track of versions in case a malicious user comes around.
	etcd := c.MustGet("etcd").(*clientv3.Client)

	r, err := etcd.Get(context.Background(), key) // TODO: context?
	if err != nil {
		log.Printf("Failed to get key: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": err})
		return
	}

	if r.Count == 0 {
		c.JSON(http.StatusNotFound, gin.H{})
		return
	}

	// r.Count should be 1
	kv := r.Kvs[0]
	dataKey := string(kv.Value)

	obj := gin.H{
		"data_key":      dataKey,
		"prev_revision": nil,
	}
	if kv.ModRevision != kv.CreateRevision {
		obj["prev_revision"] = kv.ModRevision - 1
	}

	c.JSON(http.StatusOK, obj)
}

type DataKeyPayload struct {
	DataKey string `json:"data_key"`
}

func putDataKey(c *gin.Context) {
	column := c.Params.ByName("column")
	table := c.Params.ByName("table")
	key := fmt.Sprintf("%v/%v", table, column)

	etcd := c.MustGet("etcd").(*clientv3.Client)

	var d DataKeyPayload
	err := c.BindJSON(&d)
	if err != nil {
		log.Printf("Failed to decode payload: %v\n", err)
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "json decoding: " + err.Error(),
		})
		return
	}

	_, err = etcd.Put(context.Background(), key, d.DataKey)
	if err != nil {
		log.Printf("Failed to insert data key: %v\n", err)
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "etcd insert: " + err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{})
}

func deleteDataKey(c *gin.Context) {
	column := c.Params.ByName("column")
	table := c.Params.ByName("table")
	key := fmt.Sprintf("%v/%v", table, column)

	etcd := c.MustGet("etcd").(*clientv3.Client)

	_, err := etcd.Delete(context.Background(), key)
	if err != nil {
		log.Printf("Failed to delete data key: %v\n", err)
		c.JSON(http.StatusBadRequest, gin.H{
			"error": "etcd delete: " + err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{})
}

type setupCfg struct {
	etcd clientv3.Config
}

func setupRouter(cfg setupCfg) *gin.Engine {
	router := gin.Default()

	router.Use(Etcd(cfg.etcd))
	router.GET("/keys/:table/:column", getDataKey)
	router.PUT("/keys/:table/:column", putDataKey)
	router.DELETE("/keys/:table/:column", deleteDataKey)
	return router
}

// Start up etcd before running `go run main.go`
func main() {
	cfg := setupCfg{
		etcd: clientv3.Config{
			Endpoints:   []string{"http://127.0.0.1:2379"},
			DialTimeout: 5 * time.Second,
		},
	}
	router := setupRouter(cfg)
	err := router.Run()
	if err != nil {
		log.Printf("Failed to start gin: %v\n", err)
	}
}
