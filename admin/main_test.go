package main

import (
	"encoding/json"
	"github.com/stretchr/testify/assert"
	"go.etcd.io/etcd/clientv3"
	"go.etcd.io/etcd/integration"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestPutGet(t *testing.T) {
	cluster := integration.NewClusterV3(t, &integration.ClusterConfig{Size: 1})
	defer cluster.Terminate(t)

	cfg := setupCfg{
		etcd: clientv3.Config{
			Endpoints:   []string{cluster.Members[0].GRPCAddr()},
			DialTimeout: 5 * time.Second,
		},
	}
	router := setupRouter(cfg)

	// 1. GET before PUT should return nothing
	w := httptest.NewRecorder()
	req, _ := http.NewRequest(http.MethodGet, "/keys/my_table/my_column", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	// PUT should succeed
	w = httptest.NewRecorder()
	body1 := strings.NewReader("{ \"data_key\": \"abdefg\" }")
	req, _ = http.NewRequest(http.MethodPut, "/keys/my_table/my_column", body1)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// GET after PUT should fetch expected value
	w = httptest.NewRecorder()
	req, _ = http.NewRequest(http.MethodGet, "/keys/my_table/my_column", nil)
	router.ServeHTTP(w, req)
	var data1 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &data1)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "abdefg", data1["data_key"])
	assert.Equal(t, nil, data1["prev_revision"])

	// 2nd PUT should succeed
	w = httptest.NewRecorder()
	body2 := strings.NewReader("{ \"data_key\": \"hijklmnop\" }")
	req, _ = http.NewRequest(http.MethodPut, "/keys/my_table/my_column", body2)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// GET after PUT should return the 2nd version
	w = httptest.NewRecorder()
	req, _ = http.NewRequest(http.MethodGet, "/keys/my_table/my_column", nil)
	router.ServeHTTP(w, req)
	var data2 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &data2)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "hijklmnop", data2["data_key"])
	assert.NotEqual(t, nil, data2["prev_revision"])

	// DELETE should succeed
	w = httptest.NewRecorder()
	req, _ = http.NewRequest(http.MethodDelete, "/keys/my_table/my_column", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// GET after DELETE should return nothing
	w = httptest.NewRecorder()
	req, _ = http.NewRequest(http.MethodGet, "/keys/my_table/my_column", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

}
