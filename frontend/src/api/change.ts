import { liveChangeRequest, changeBase, changeToken, changeHeaders } from "./changeSession";
const request = {
  get: (path:string, params?:object) => liveChangeRequest(path, 'GET', undefined, params),
  post: (path:string, body?:any) => liveChangeRequest(path, 'POST', body),
  put: (path:string, body?:any) => liveChangeRequest(path, 'PUT', body),
  del: (path:string, params?:object) => liveChangeRequest(path, 'DELETE', undefined, params),
};

/** Dedicated to the live change workflow; other modules retain their existing API. */
export const changes = {
  page: (params: object) => request.get("/changes", params),
  context: (projectId?: number) =>
    request.get("/changes/context", { projectId }),
  detail: (id: number) => request.get(`/changes/${id}`),
  save: (body: any) =>
    body.id
      ? request.put(`/changes/${body.id}`, body)
      : request.post("/changes", body),
  submit: (id: number, revision: number) =>
    request.post(`/changes/${id}/submit`, { revision }),
  review: (id: number, body: object) =>
    request.post(`/changes/${id}/audit`, body),
  archive: (id: number, body: object) =>
    request.post(`/changes/${id}/archive`, body),
  remove: (id: number, revision: number) =>
    request.del(`/changes/${id}`, { revision }),
  upload: (id: number, revision: number, kind: string, file: File) => {
    const data = new FormData();
    data.append("file", file);
    return request.post(
      `/changes/${id}/files?revision=${revision}&kind=${kind}`,
      data,
    );
  },
  removeFile: (id: number, fileId: number, revision: number) =>
    request.del(`/changes/${id}/files/${fileId}`, { revision }),
  async download(id: number, file: { id: number; fileName: string }) {
    const base = changeBase();
    const response = await fetch(
      `${base}/changes/${id}/files/${file.id}/download`,
      {
        headers: changeHeaders(),
      },
    );
    if (
      !response.ok ||
      response.headers.get("content-type")?.includes("application/json")
    ) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.msg || "附件下载失败，请刷新后重试");
    }
    const url = URL.createObjectURL(await response.blob());
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = file.fileName;
    anchor.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
