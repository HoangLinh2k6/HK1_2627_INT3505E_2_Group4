"""Generate Đặt Sân design figures as SVG, PNG and editable PlantUML.

Run with Python 3 and Pillow. No network or diagram server is required.
"""

from pathlib import Path
from html import escape
import math

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
INK = "#203044"
MUTED = "#526277"
LINE = "#64748b"
BLUE = "#155e75"
PALE = "#edf6f8"
GRAY = "#f4f6f8"
SCALE = 2
FONT_ROOT = Path("C:/Windows/Fonts")


class Figure:
    def __init__(self, name, title, subtitle, height=1120):
        self.name = name
        self.width, self.height = 1600, height
        self.image = Image.new("RGB", (1600 * SCALE, height * SCALE), "white")
        self.draw = ImageDraw.Draw(self.image)
        self.svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="{height}" viewBox="0 0 1600 {height}">', '<rect width="100%" height="100%" fill="white"/>']
        self.labels = []
        self.text(55, 35, "ĐẶT SÂN", 21, BLUE, True)
        self.text(55, 76, title, 35, INK, True)
        self.text(55, 125, subtitle, 21, MUTED)

    def text(self, x, y, value, size=23, color=INK, bold=False, center=False, background=False):
        font = ImageFont.truetype(str(FONT_ROOT / ("arialbd.ttf" if bold else "arial.ttf")), size * SCALE)
        width = self.draw.textlength(value, font=font) / SCALE
        if center:
            x -= width / 2
        if background:
            self.rect(x - 7, y - 3, width + 14, size + 8, "white", "white", 0)
        self.draw.text((x * SCALE, y * SCALE), value, font=font, fill=color)
        self.svg.append(f'<text x="{x:.1f}" y="{y + size * .87:.1f}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{700 if bold else 400}" fill="{color}">{escape(value)}</text>')

    def rect(self, x, y, w, h, fill="white", stroke=LINE, radius=10, dashed=False):
        box = (x * SCALE, y * SCALE, (x + w) * SCALE, (y + h) * SCALE)
        self.draw.rounded_rectangle(box, radius=radius * SCALE, fill=fill, outline=stroke, width=2 * SCALE)
        dash = ' stroke-dasharray="9 7"' if dashed else ""
        self.svg.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="2"{dash}/>')

    def path(self, points, arrow=False, dashed=False, color=LINE, width=2):
        coords = [(x * SCALE, y * SCALE) for x, y in points]
        if dashed:
            for (x1, y1), (x2, y2) in zip(coords, coords[1:]):
                distance = math.hypot(x2 - x1, y2 - y1)
                for start in range(0, math.ceil(distance), 16 * SCALE):
                    end = min(start + 9 * SCALE, distance)
                    self.draw.line((x1 + (x2-x1)*start/distance, y1 + (y2-y1)*start/distance,
                                    x1 + (x2-x1)*end/distance, y1 + (y2-y1)*end/distance), fill=color, width=width*SCALE)
        else:
            self.draw.line(coords, fill=color, width=width*SCALE, joint="curve")
        dash = ' stroke-dasharray="9 7"' if dashed else ""
        self.svg.append(f'<polyline points="{" ".join(f"{x},{y}" for x,y in points)}" fill="none" stroke="{color}" stroke-width="{width}"{dash}/>')
        if arrow:
            x, y = points[-1]
            px, py = points[-2]
            angle = math.atan2(y-py, x-px)
            triangle = [(x, y), (x-13*math.cos(angle-.42), y-13*math.sin(angle-.42)), (x-13*math.cos(angle+.42), y-13*math.sin(angle+.42))]
            self.draw.polygon([(a*SCALE,b*SCALE) for a,b in triangle], fill=color)
            self.svg.append(f'<polygon points="{" ".join(f"{a},{b}" for a,b in triangle)}" fill="{color}"/>')

    def entity(self, x, y, name, fields, w=400):
        h = 58 + 34 * len(fields) + 12
        self.rect(x, y, w, h)
        self.rect(x, y, w, 56, PALE, LINE, 0)
        self.text(x + 16, y + 12, name, 25, BLUE, True)
        for i, field in enumerate(fields):
            self.text(x + 16, y + 69 + 34 * i, field, 22)
        return h

    def box(self, x, y, w, h, title, lines=(), fill=PALE):
        self.rect(x, y, w, h, fill)
        self.text(x + 18, y + 15, title, 25, BLUE, True)
        for i, line in enumerate(lines):
            self.text(x + 18, y + 57 + i * 33, line, 22)

    def relation(self, points, left, right):
        self.path(points)
        a, b = points[0], points[-1]
        dx, dy = points[1][0] - a[0], points[1][1] - a[1]
        ex, ey = b[0] - points[-2][0], b[1] - points[-2][1]
        start_x = a[0] + (16 if dx > 0 else -55 if dx < 0 else 14)
        start_y = a[1] + (10 if dy > 0 else -37)
        end_x = b[0] + (-65 if ex > 0 else 18 if ex < 0 else 14)
        end_y = b[1] + (12 if ey < 0 else -37)
        self.labels.extend([(start_x, start_y, left), (end_x, end_y, right)])

    def actor(self, x, y, name):
        self.draw.ellipse(((x-15)*SCALE,y*SCALE,(x+15)*SCALE,(y+30)*SCALE), outline=INK,width=2*SCALE)
        self.svg.append(f'<circle cx="{x}" cy="{y+15}" r="15" fill="white" stroke="{INK}" stroke-width="2"/>')
        for points in [[(x,y+30),(x,y+85)],[(x-30,y+50),(x+30,y+50)],[(x,y+85),(x-26,y+120)],[(x,y+85),(x+26,y+120)]]:
            self.path(points,color=INK)
        self.text(x,y+132,name,24,bold=True,center=True)

    def oval(self, x, y, w, h, lines):
        self.draw.ellipse((x*SCALE,y*SCALE,(x+w)*SCALE,(y+h)*SCALE),fill=PALE,outline=LINE,width=2*SCALE)
        self.svg.append(f'<ellipse cx="{x+w/2}" cy="{y+h/2}" rx="{w/2}" ry="{h/2}" fill="{PALE}" stroke="{LINE}" stroke-width="2"/>')
        top = y + (h-len(lines)*29)/2
        for i,line in enumerate(lines):
            self.text(x+w/2,top+i*29,line,23,center=True)

    def footer(self, lines):
        y = self.height - 40 - 29 * len(lines)
        self.path([(55,y-17),(1545,y-17)],color="#cbd5e1")
        for i,line in enumerate(lines):
            self.text(55,y+i*29,line,20,MUTED)

    def save(self, source):
        for x, y, label in self.labels:
            self.text(x, y, label, 20, background=True)
        for sub in ("png", "svg", "sources"):
            (ROOT / sub).mkdir(parents=True, exist_ok=True)
        self.image.save(ROOT / "png" / f"{self.name}.png", dpi=(300,300))
        (ROOT / "svg" / f"{self.name}.svg").write_text("\n".join(self.svg + ["</svg>"]), encoding="utf-8")
        preamble = '@startuml\nskinparam defaultFontName Arial\nskinparam shadowing false\nskinparam backgroundColor white\nskinparam classAttributeIconSize 0\n'
        (ROOT / "sources" / f"{self.name}.puml").write_text(preamble + source + "\n@enduml\n",encoding="utf-8")


def resource_model():
    f = Figure("01-resource-model", "Resource model và định danh API", "Các resource nghiệp vụ chính và quan hệ từ góc nhìn API consumer.", 1120)
    # Relationships express domain associations. Arrows are deliberately omitted.
    for pts,a,b in [([(430,290),(600,290)],"1","0..*"), ([(1000,290),(1170,290)],"1","0..*"),
                    ([(1370,400),(1370,535)],"1","0..*"), ([(430,335),(500,335),(500,485),(1240,485),(1240,535)],"1","0..*"),
                    ([(1170,650),(1000,650)],"1","0..*"),
                    ([(1370,740),(1370,925),(1000,925)],"1","0..1")]:
        f.relation(pts,a,b)
    f.box(55,220,375,180,"Người dùng",["/users/me", "id, email, fullName, role"])
    f.box(600,220,400,180,"Cơ sở thể thao",["/venues/{venueId}","id, owner, name, city"])
    f.box(1170,220,375,180,"Sân trong cơ sở",["/courts/{courtId}","id, venue, sportType, price"])
    f.box(55,535,375,205,"Danh mục môn",["/sport-types/{id}","Một môn có nhiều sân.","Mỗi sân thuộc một môn."])
    f.relation([(430,575),(465,575),(465,510),(1100,510),(1100,350),(1170,350)],"1","0..*")
    f.box(600,535,400,205,"Khoản thanh toán",["/payments/{paymentId}","booking → 0..* payments", "amount, method, paidAt"])
    f.box(1170,535,375,205,"Lịch đặt",["/bookings/{bookingId}","court, user, startTime,", "endTime, totalPrice, status"])
    f.box(600,850,400,150,"Đánh giá",["/reviews/{reviewId}","booking, venue, user, rating"])
    f.text(55,830,"Một đánh giá thuộc một lịch đặt,",21,MUTED)
    f.text(55,863,"một người viết và một cơ sở.",21,MUTED)
    f.footer(["Định danh backend hiện tại là số nguyên Long. Venue là cơ sở, Court là sân vật lý có lịch riêng.",
              "GET /courts/{id}/availability và /quote là dữ liệu tính toán; /confirm, /cancel, /complete là thao tác nghiệp vụ."])
    f.save(r'''title Đặt Sân - Resource model
left to right direction
object "User\n/users/me" as U
object "Venue\n/venues/{id}" as V
object "Court\n/courts/{id}" as C
object "SportType\n/sport-types/{id}" as S
object "Booking\n/bookings/{id}" as B
object "Payment\n/payments/{id}" as P
object "Review\n/reviews/{id}" as R
U "1" -- "0..*" V : sở hữu
V "1" -- "0..*" C : chứa
S "1" -- "0..*" C : phân loại
U "1" -- "0..*" B : tạo
C "1" -- "0..*" B : được đặt
B "1" -- "0..*" P : có
B "1" -- "0..1" R : được đánh giá
note bottom of R : Mỗi đánh giá còn thuộc một người viết\nvà một cơ sở; chi tiết khóa ngoại xem ERD.
note bottom of C
  GET /courts/{id}/availability
  GET /courts/{id}/quote
  Dữ liệu tính toán từ sân và lịch đặt.
end note''')


def erd_core():
    f = Figure("02-ERD-core", "ERD dữ liệu cốt lõi và phiên đăng nhập", "Ánh xạ entity JPA hiện tại • thuộc tính chính • PK: khóa chính, FK: khóa ngoại, UK: duy nhất.", 1550)
    positions = {
        "users": (55,200,["PK id : BIGINT", "UK email : VARCHAR", "password_hash : VARCHAR", "full_name, phone : VARCHAR", "role : ENUM"]),
        "venues": (600,200,["PK id : BIGINT", "FK owner_id → users", "name, address, district, city", "open_time, close_time : TIME", "description, status"]),
        "sport_types": (1145,200,["PK id : BIGINT", "UK name : VARCHAR"]),
        "auth_sessions": (55,665,["PK id : BIGINT", "FK user_id → users", "UK access_hash : VARCHAR(64)", "UK refresh_hash : VARCHAR(64)", "access_expires_at", "refresh_expires_at"]),
        "courts": (600,665,["PK id : BIGINT", "FK venue_id → venues", "FK sport_type_id → sport_types", "name, status", "base_price_per_hour : DECIMAL"]),
        "bookings": (1145,665,["PK id : BIGINT", "FK user_id → users", "FK court_id → courts", "start_time, end_time", "total_price : DECIMAL(12,2)", "status, expires_at", "request_key : VARCHAR(64)", "checked_in_at, checked_out_at"]),
        "payments": (55,1140,["PK id : BIGINT", "FK booking_id → bookings", "amount : DECIMAL(12,2)", "method, transaction_ref, paid_at"]),
        "reviews": (600,1140,["PK id : BIGINT", "FK venue_id, user_id", "FK/UK booking_id → bookings", "rating, comment, created_at"]),
    }
    links = [([(455,300),(600,300)],"1","0..*"), ([(255,440),(255,665)],"1","0..*"),
             ([(800,440),(800,665)],"1","0..*"), ([(1345,338),(1345,540),(1020,540),(1020,740),(1000,740)],"1","0..*"),
             ([(1000,850),(1145,850)],"1","0..*"), ([(455,370),(500,370),(500,600),(1210,600),(1210,665)],"1","0..*"),
             ([(1345,1007),(1345,1090),(255,1090),(255,1140)],"1","0..*"),
             ([(1470,1007),(1470,1300),(1000,1300)],"1","0..1"),
             ([(650,440),(550,440),(550,1190),(600,1190)],"1","0..*"),
             ([(70,440),(30,440),(30,1390),(800,1390),(800,1346)],"1","0..*")]
    for pts,a,b in links:
        f.relation(pts,a,b)
    for name,(x,y,fields) in positions.items():
        f.entity(x,y,name,fields)
    f.footer(["bookings có UNIQUE(user_id, request_key) và index (court_id, start_time, end_time).",
              "Quan hệ 1 — 0..* cho phép phía nhiều chưa có bản ghi; mỗi bản ghi con có đúng một bản ghi cha.",
              "Review có FK riêng đến User và Venue; ứng dụng kiểm tra chúng khớp với Booking. Giờ nghiệp vụ theo Việt Nam."])
    source = "title Đặt Sân - ERD cốt lõi\nhide methods\n"
    for name,(_,_,fields) in positions.items():
        source += f'entity "{name}" as {name} {{\n' + "\n".join("  "+line for line in fields) + "\n}\n"
    for a,b,many in [("users","venues","0..*"),("users","auth_sessions","0..*"),("users","bookings","0..*"),("venues","courts","0..*"),("sport_types","courts","0..*"),("courts","bookings","0..*"),("bookings","payments","0..*"),("bookings","reviews","0..1"),("users","reviews","0..*"),("venues","reviews","0..*")]:
        source += f'{a} "1" -- "{many}" {b}\n'
    source += 'note bottom of bookings : UNIQUE(user_id, request_key)\n'
    f.save(source)


def erd_extension():
    f = Figure("03-ERD-services", "ERD dịch vụ bổ sung và thuê dụng cụ", "Các bảng đã xuất hiện trong backend • liên kết với venues và bookings của ERD cốt lõi.", 1450)
    data = {
        "venues": (55,200,["PK id", "Cơ sở sở hữu dịch vụ"]),
        "venue_addons": (600,200,["PK id; FK venue_id", "name, kind", "unit_price, stock_on_hand", "active"]),
        "equipment_assets": (1145,200,["PK id; FK venue_id, addon_id", "code, state", "UK (venue_id, code)"]),
        "bookings": (55,650,["PK id", "Lịch đặt sử dụng dịch vụ"]),
        "addon_orders": (600,650,["PK id; FK booking_id, addon_id", "request_key, item_name", "quantity, unit_price", "ordered_at, cancelled_at", "delivered_at, delivered_by"]),
        "equipment_rentals": (1145,650,["PK id; FK order_id, asset_id", "handed_out_at, returned_at", "returned_by", "UK (order_id, asset_id)"]),
        "rental_photos": (1145,1040,["PK id; FK rental_id", "phase, angle, content_type", "cloudinary_public_id", "content_sha256, uploaded_at", "image : MEDIUMBLOB (legacy)"]),
    }
    for pts,a,b in [([(455,265),(600,265)],"1","0..*"), ([(1000,265),(1145,265)],"1","0..*"),
                     ([(255,200),(255,175),(1345,175),(1345,200)],"1","0..*"),
                     ([(800,406),(800,650)],"1","0..*"), ([(455,720),(600,720)],"1","0..*"),
                     ([(1000,755),(1145,755)],"1","0..*"), ([(1345,372),(1345,650)],"1","0..*"),
                     ([(1345,856),(1345,1040)],"1","0..*")]:
        f.relation(pts,a,b)
    for name,(x,y,fields) in data.items():
        f.entity(x,y,name,fields)
    f.box(55,1035,945,170,"Ràng buộc và lưu vết",["addon_orders có UNIQUE(booking_id, request_key).", "rental_photos có UNIQUE(rental_id, phase, angle).", "delivered_by và returned_by là ID lưu vết; entity hiện dùng Long."],GRAY)
    f.footer(["kind: CONSUMABLE hoặc RENTAL. phase: CHECK_IN / CHECK_OUT. angle: OVERVIEW / DETAIL.",
              "Mỗi lượt thuê có tối đa một ảnh được lưu cho từng cặp phase / angle, theo ràng buộc duy nhất của bảng."])
    source = "title Đặt Sân - ERD dịch vụ bổ sung\nhide methods\n"
    for name,(_,_,fields) in data.items():
        source += f'entity "{name}" as {name} {{\n' + "\n".join("  "+line for line in fields) + "\n}\n"
    for a,b in [("venues","venue_addons"),("venues","equipment_assets"),("venue_addons","equipment_assets"),("venue_addons","addon_orders"),("bookings","addon_orders"),("addon_orders","equipment_rentals"),("equipment_assets","equipment_rentals"),("equipment_rentals","rental_photos")]:
        source += f'{a} "1" -- "0..*" {b}\n'
    source += 'note bottom of rental_photos : UNIQUE(rental_id, phase, angle)\n'
    f.save(source)


def architecture():
    f = Figure("04-system-architecture", "Kiến trúc hệ thống", "Hiện trạng mã nguồn • backend Spring Boot dùng chung một ứng dụng và cơ sở dữ liệu MySQL.", 1200)
    f.rect(55,205,600,585,GRAY)
    f.text(75,224,"Trình duyệt / React + Vite",26,BLUE,True)
    f.box(85,285,540,135,"Giao diện",["Khách hàng · Chủ sân · Quản trị viên", "Trang, form, routing, AuthContext"])
    f.box(85,475,540,135,"Lớp gọi API",["services.js → client.js → backendAdapter.js", "Chuẩn hóa dữ liệu giữa UI và backend"])
    f.path([(350,420),(350,475)],True)
    f.box(85,665,540,95,"Auth cục bộ trong adapter",["Cần nối login / refresh / logout với backend"],GRAY)
    f.path([(350,610),(350,665)],True,dashed=True)
    f.box(785,205,760,530,"Backend · Spring Boot / Java 21",[],GRAY)
    f.box(820,280,690,90,"Spring Security và AccessRules",["Bearer token · vai trò · quyền trên tài nguyên"])
    f.box(820,405,690,125,"REST controllers → nghiệp vụ",["Auth · Venue · Court · Booking · Review · Payment", "AddonService · quản lý dụng cụ và ảnh thuê"])
    f.box(820,565,690,115,"Repository / lưu trữ",["Spring Data JPA · EntityManager · AddonStore", "Transaction và khóa phục vụ nghiệp vụ đặt sân"])
    f.path([(1165,370),(1165,405)],True)
    f.path([(1165,530),(1165,565)],True)
    f.path([(625,530),(720,530),(720,325),(820,325)],True)
    f.text(690,390,"HTTP",21,BLUE,True,center=True,background=True)
    f.text(690,420,"JSON",21,MUTED,center=True,background=True)
    f.path([(625,710),(755,710),(755,350),(820,350)],True,dashed=True)
    f.box(820,810,335,155,"MySQL",["Dữ liệu nghiệp vụ", "auth_sessions", "Ảnh cũ có thể còn BLOB"])
    f.box(1210,810,335,155,"Cloudinary",["Mã tích hợp ảnh thuê", "Tài nguyên có xác thực", "Link xem có thời hạn"])
    f.path([(995,680),(995,810)],True)
    f.path([(1390,530),(1570,530),(1570,775),(1390,775),(1390,810)],True)
    f.box(55,850,600,125,"Chế độ mock phục vụ phát triển UI",["client.js → mocks/transport.js → fixtures", "Bộ nhớ của tab; VITE_API_MODE=mock"],GRAY)
    f.footer(["Vite proxy phát triển: /api hoặc /api/v1 → backend, bỏ prefix. Backend hiện khai báo /auth, /venues, /courts...",
              "Nét đứt đánh dấu luồng auth còn cần tích hợp. Sơ đồ thể hiện cấu trúc mã; tích hợp runtime cần kiểm thử tiếp."])
    f.save(r'''title Đặt Sân - Kiến trúc hệ thống
component "React UI" as UI
component "services.js / client.js" as Client
component "backendAdapter.js" as Adapter
component "Auth cục bộ\nCần tích hợp backend" as LocalAuth
component "Browser mock\ntransport.js / fixtures" as Mock
node "Spring Boot / Java 21" {
  component "Spring Security / AccessRules" as Sec
  component "REST controllers" as Ctrl
  component "Service nghiệp vụ" as Svc
  component "JPA / EntityManager / AddonStore" as Repo
}
database MySQL as DB
cloud "Cloudinary\nẢnh thuê có xác thực" as Cloud
UI --> Client
Client --> Adapter : HTTP mode
Client --> Mock : mock mode
Adapter --> Sec : HTTP JSON / Vite proxy
Adapter ..> LocalAuth
LocalAuth ..> Sec : cần tích hợp /auth/*
Sec --> Ctrl
Ctrl --> Svc
Svc --> Repo
Repo --> DB
Svc --> Cloud : ảnh thuê
note bottom of DB
  Backend triển khai chung một ứng dụng.
  Các quan hệ thể hiện cấu trúc mã hiện tại.
end note''')


def usecase():
    f = Figure("05-UML-use-case", "UML use case · phạm vi chức năng chính", "Ba vai trò sử dụng Đặt Sân • các đường nối thể hiện quyền tham gia use case.", 1300)
    f.rect(325,200,950,900,GRAY)
    f.text(800,218,"Hệ thống Đặt Sân",26,BLUE,True,center=True)
    # Draw associations before the actors and use cases.
    cases = [(430,280,["Tìm cơ sở", "và xem sân"]),(430,455,["Xem lịch trống", "và báo giá"]),
             (430,630,["Đặt, xem và", "hủy lịch của mình"]),(430,805,["Đánh giá lịch", "đã hoàn thành"]),
             (865,280,["Đăng nhập", "Cập nhật hồ sơ"]),(865,455,["Quản lý cơ sở", "và sân của mình"]),
             (865,630,["Xem, xác nhận, hủy", "và hoàn thành lịch"]),(865,805,["Quản lý danh mục", "môn thể thao"])]
    for target in [(430,335),(430,510),(430,685),(430,860)]:
        f.path([(165,540), (290,target[1]), target])
    f.path([(165,540),(290,260),(1040,260),(1040,280)])
    f.path([(165,540),(290,1020),(620,1020)])
    for target in [(1215,335),(1215,510),(1215,685)]:
        f.path([(1435,540),(1310,target[1]),target])
    f.path([(1435,915),(1215,860)])
    f.path([(1435,915),(1325,915),(1325,335),(1215,335)])
    for x,y,lines in cases:
        f.oval(x,y,350,110,lines)
    f.oval(620,965,350,100,["Đăng ký tài khoản", "khách hàng"])
    f.actor(165,465,"Khách hàng")
    f.actor(1435,465,"Chủ sân")
    f.actor(1435,835,"Quản trị viên")
    f.footer(["Tìm sân, xem lịch và giá được phép truy cập công khai. Các thao tác riêng yêu cầu phiên và quyền phù hợp.",
              "Đăng ký công khai tạo CUSTOMER. Tài khoản OWNER / ADMIN được cấp qua công cụ quản trị backend."])
    f.save(r'''title Đặt Sân - UML use case
left to right direction
actor "Khách hàng" as Customer
actor "Chủ sân" as Owner
actor "Quản trị viên" as Admin
rectangle "Hệ thống Đặt Sân" {
  usecase "Tìm cơ sở và xem sân" as Search
  usecase "Xem lịch trống và báo giá" as Quote
  usecase "Đặt, xem và hủy lịch của mình" as Book
  usecase "Đánh giá lịch đã hoàn thành" as Review
  usecase "Đăng nhập / hồ sơ" as Login
  usecase "Đăng ký khách hàng" as Register
  usecase "Quản lý cơ sở và sân của mình" as Manage
  usecase "Xem, xác nhận, hủy và hoàn thành lịch" as Confirm
  usecase "Quản lý danh mục môn thể thao" as Sports
}
Customer -- Search
Customer -- Quote
Customer -- Book
Customer -- Review
Customer -- Login
Customer -- Register
Owner -- Login
Owner -- Manage
Owner -- Confirm
Admin -- Login
Admin -- Sports
note bottom of Book : Backend kiểm tra phiên, vai trò và quyền trên từng tài nguyên.''')


def sequence():
    f = Figure("06-UML-booking-sequence", "UML sequence · kiểm tra giờ, báo giá và đặt sân", "Consumer gọi trực tiếp API backend • điều kiện đầu vào: đã có access token hợp lệ của CUSTOMER.", 2110)
    xs = [170,585,1005,1435]
    names = ["API consumer", "Security / Controller", "Service / CourtSchedule", "Repository / MySQL"]
    for x,name in zip(xs,names):
        f.rect(x-145,200,290,65,PALE)
        f.text(x,220,name,21,BLUE,True,center=True)
        f.path([(x,265),(x,1940)],dashed=True,color="#b0bac7")

    def message(a,b,y,label,reply=False):
        f.path([(xs[a],y),(xs[b],y)],True,dashed=reply,color=BLUE if not reply else LINE)
        f.text((xs[a]+xs[b])/2,y-31,label,21,center=True,background=True)

    message(0,1,325,"GET /courts/{id}/availability?date=...")
    message(1,2,385,"Kiểm tra sân / tính khung giờ")
    message(2,3,445,"Đọc sân và các khoảng đã giữ / đặt")
    message(3,2,505,"Dữ liệu sân và booking",True)
    message(1,0,565,"200 · danh sách khung giờ",True)
    message(0,1,635,"GET /courts/{id}/quote?start=...&end=...")
    message(1,2,695,"Tính giá theo slot và kiểm tra khả dụng")
    message(1,0,755,"200 · tổng giá và khả dụng tại thời điểm hỏi",True)
    message(0,1,835,"POST /bookings + Bearer token + Idempotency-Key (tùy chọn)")
    message(1,2,895,"Lấy user từ phiên; kiểm tra khoảng giờ")
    message(2,3,955,"Nếu có key: khóa user, tra (user_id, request_key)")
    f.rect(75,995,1450,930,"white",LINE,0)
    for x in xs:
        f.path([(x,1035),(x,1925)],dashed=True,color="#b0bac7")
    f.text(95,1007,"alt [key đã tồn tại và nội dung khớp]",22,BLUE,True,background=True)
    message(2,1,1070,"Booking cũ",True)
    message(1,0,1130,"201 · booking cũ",True)
    f.path([(75,1170),(1525,1170)],dashed=True)
    f.text(95,1183,"else [key đã tồn tại, nội dung khác]",22,BLUE,True,background=True)
    message(2,1,1245,"Xung đột Idempotency-Key",True)
    message(1,0,1305,"409 Conflict",True)
    f.path([(75,1345),(1525,1345)],dashed=True)
    f.text(95,1358,"else [key mới hoặc không gửi key]",22,BLUE,True,background=True)
    message(2,3,1420,"Trong transaction · khóa Court (PESSIMISTIC_WRITE)")
    message(2,3,1480,"Kiểm tra sân, giờ mở cửa, giá và khoảng giao nhau")
    f.rect(100,1520,1400,375,"white",LINE,0)
    for x in xs:
        f.path([(x,1555),(x,1895)],dashed=True,color="#b0bac7")
    f.text(120,1532,"alt [trùng lịch / dữ liệu không hợp lệ]",22,BLUE,True,background=True)
    message(2,1,1590,"Lỗi nghiệp vụ",True)
    message(1,0,1645,"409 trùng lịch · 400 giờ sai · 404 sân thiếu / bị ẩn",True)
    f.path([(100,1680),(1500,1680)],dashed=True)
    f.text(120,1693,"else [hợp lệ]",22,BLUE,True,background=True)
    message(2,3,1750,"Tính giá · INSERT PENDING · COMMIT")
    message(3,2,1795,"Booking đã lưu",True)
    message(2,1,1840,"Booking",True)
    message(1,0,1885,"201 Created + booking",True)
    f.footer(["POST backend nhận {court: {id}, startTime, endTime}. Giá và user được backend xác định.",
              "Quote không giữ chỗ; tạo booking kiểm tra lại. expiresAt = min(now + 30 phút, startTime). Một số lần đọc được gộp."])
    f.save(r'''title Đặt Sân - UML sequence đặt sân
actor "API consumer" as C
participant "Security / Controller" as A
participant "Service / CourtSchedule" as S
database "Repository / MySQL" as D
note over C,A : CUSTOMER có access token hợp lệ.\nConsumer dùng trực tiếp HTTP API backend.
C -> A : GET /courts/{id}/availability?date=...
A -> S : Đọc lịch khả dụng
S -> D : Đọc sân và khoảng đã giữ / đặt
D --> S : Dữ liệu lịch
S --> A : Khung giờ
A --> C : 200 + danh sách khung giờ
C -> A : GET /courts/{id}/quote?start=...&end=...
A -> S : Tính giá và kiểm tra khả dụng
S -> D : Đọc dữ liệu giá / lịch
D --> S : Dữ liệu hiện tại
S --> A : Báo giá
A --> C : 200 + tổng giá / available
C -> A : POST /bookings + Bearer token\nIdempotency-Key tùy chọn\n{court:{id}, startTime, endTime}
A -> A : Xác thực CUSTOMER; lấy user từ phiên
A -> S : createBooking()
S -> S : Kiểm tra khoảng thời gian
opt Có Idempotency-Key
  S -> D : Khóa User; tra (user_id, request_key)
  D --> S : Booking cũ hoặc rỗng
end
alt Có booking cũ cùng nội dung
  S --> A : Booking cũ
  A --> C : 201 + booking cũ
else Key cũ có nội dung khác
  S --> A : Conflict
  A --> C : 409
else Tạo mới
  group Transaction READ_COMMITTED
    S -> D : Khóa Court bằng PESSIMISTIC_WRITE
    S -> S : Kiểm tra sân, giờ mở cửa và giá
    S -> D : existsOccupied(courtId, start, end, now)
    D --> S : Có / không xung đột
    alt Trùng lịch
      S --> A : Conflict
      A --> C : 409
    else Hợp lệ
      S -> S : Tính lại giá; status = PENDING\nexpiresAt = min(now + 30 phút, startTime)
      S -> D : INSERT booking; COMMIT
      D --> S : Booking đã lưu
      S --> A : Booking
      A --> C : 201 Created + booking
    end
  end
end
note over C,D : Quote không giữ chỗ. Khoảng giờ sai trả 400; sân không tồn tại / bị ẩn trả 404.''')


def state():
    f = Figure("07-UML-booking-states", "UML state machine · vòng đời lịch đặt", "Theo BookingServiceImpl, BookingHoldExpiry và luồng check-out của AddonService.", 1220)
    f.draw.ellipse((70*SCALE,325*SCALE,94*SCALE,349*SCALE),fill=INK)
    f.svg.append(f'<circle cx="82" cy="337" r="12" fill="{INK}"/>')
    f.path([(95,337),(275,337)],True)
    f.text(165,280,"Tạo hợp lệ",21,center=True,background=True)
    f.path([(655,337),(980,337)],True)
    f.text(810,252,"Chủ sân xác nhận",22,center=True,background=True)
    f.text(810,283,"[chưa hết hạn, chưa bắt đầu]",20,center=True,background=True)
    f.path([(465,420),(465,690)],True)
    f.text(465,545,"Hết hạn giữ chỗ",22,center=True,background=True)
    f.path([(655,390),(850,390),(850,775),(980,775)],True)
    f.text(820,600,"Hủy",22,center=True,background=True)
    f.path([(1170,420),(1170,690)],True)
    f.text(1170,545,"Hủy [chưa check-in]",22,center=True,background=True)
    f.path([(1360,337),(1545,337),(1545,955),(1530,955)],True)
    f.text(1450,455,"Hoàn thành",20,center=True,background=True)
    f.box(275,265,380,155,"PENDING",["Giữ chỗ tối đa 30 phút", "hoặc đến giờ bắt đầu"])
    f.box(980,265,380,155,"CONFIRMED",["Chủ sân đã xác nhận", "Chiếm lịch sân"])
    f.box(275,690,380,135,"EXPIRED",["Giữ chỗ đã hết hiệu lực"])
    f.box(980,690,380,135,"CANCELLED",["Lịch đã bị hủy"])
    f.box(1150,900,380,110,"COMPLETED",["Đã hoàn thành sử dụng"])
    f.box(55,895,1020,115,"Điều kiện hoàn thành",["/complete: CONFIRMED, đã hết giờ, chưa check-in và không có đơn dịch vụ đang hiệu lực.", "/check-out: đã check-in, CONFIRMED, đáp ứng điều kiện trả dụng cụ / ảnh."],GRAY)
    f.footer(["Scheduler cập nhật PENDING → EXPIRED khi expiresAt ≤ now hoặc startTime ≤ now; mặc định kiểm tra mỗi 60 giây.",
              "check-in giữ nguyên CONFIRMED. Thanh toán có dữ liệu riêng; không thêm trạng thái PAID vào Booking."])
    f.save(r'''title Đặt Sân - UML vòng đời Booking
[*] --> PENDING : Tạo booking hợp lệ
PENDING --> CONFIRMED : Chủ sân xác nhận\n[chưa hết hạn và chưa bắt đầu]
PENDING --> CANCELLED : Hủy
PENDING --> EXPIRED : Scheduler\n[expiresAt <= now hoặc startTime <= now]
CONFIRMED --> CANCELLED : Hủy [chưa check-in]
CONFIRMED --> COMPLETED : /complete [đã hết giờ, chưa check-in,\nkhông có đơn dịch vụ đang hiệu lực]
CONFIRMED --> COMPLETED : /check-out [đã check-in,\nđủ điều kiện trả dụng cụ / ảnh]
note right of CONFIRMED : check-in cập nhật checkedInAt,\nstatus vẫn là CONFIRMED.
note bottom of PENDING : expiresAt = min(now + 30 phút, startTime)
note bottom of COMPLETED : Thanh toán có dữ liệu riêng.\nBooking không có trạng thái PAID.''')


if __name__ == "__main__":
    for generate in (resource_model, erd_core, erd_extension, architecture, usecase, sequence, state):
        generate()
    print("Generated 7 figures in PNG, SVG and PlantUML source formats.")
